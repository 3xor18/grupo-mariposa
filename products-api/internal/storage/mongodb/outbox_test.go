package mongodb

import (
	"context"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"

	"github.com/grupomariposa/platform/products-api/internal/outbox"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	ownerA = "relay-a"
	ownerB = "relay-b"
	lease  = 30 * time.Second
)

func insertEvents(t *testing.T, store *Store, events ...outboxDocument) {
	t.Helper()
	for _, e := range events {
		if _, err := store.outbox.InsertOne(t.Context(), e); err != nil {
			t.Fatalf("insert: %v", err)
		}
	}
}

func pendingEvent(id, key string, version int64, createdAt time.Time) outboxDocument {
	return outboxDocument{ID: id, Topic: testTopic, Key: key, Payload: `{}`, Version: version,
		Status: statusPending, AvailableAt: createdAt, CreatedAt: createdAt}
}

func claimAt(now time.Time, owner string, limit int) outbox.Claim {
	return outbox.Claim{Limit: limit, Now: now, LeaseUntil: now.Add(lease), Owner: owner}
}

func ids(events []outbox.PendingEvent) []string {
	out := make([]string, 0, len(events))
	for _, e := range events {
		out = append(out, e.ID)
	}
	return out
}

func TestClaimOldestVersionPerKeyInCreationOrder(t *testing.T) {
	store := openStore(t)
	base := time.Now().UTC().Truncate(time.Millisecond).Add(-time.Minute)
	insertEvents(t, store,
		pendingEvent("mx-v3", "MX:PRD-001", 3, base.Add(3*time.Second)),
		pendingEvent("mx-v2", "MX:PRD-001", 2, base.Add(2*time.Second)),
		pendingEvent("cl-v2", "CL:PRD-001", 2, base.Add(time.Second)),
		pendingEvent("ec-v2", "EC:PRD-001", 2, base.Add(4*time.Second)),
	)
	now := base.Add(time.Minute)
	claimed, err := store.Claim(t.Context(), claimAt(now, ownerA, 2))
	if err != nil {
		t.Fatalf("claim: %v", err)
	}
	if got := ids(claimed); len(got) != 2 || got[0] != "cl-v2" || got[1] != "mx-v2" {
		t.Fatalf("want oldest per key in creation order, got %v", got)
	}
	second, _ := store.Claim(t.Context(), claimAt(now, ownerB, 10))
	if got := ids(second); len(got) != 1 || got[0] != "ec-v2" {
		t.Fatalf("leased keys must block newer versions, got %v", got)
	}
}

func TestLeaseFencingAndLifecycle(t *testing.T) {
	store := openStore(t)
	now := time.Now().UTC().Truncate(time.Millisecond)
	insertEvents(t, store, pendingEvent("evt-1", "MX:PRD-001", 2, now.Add(-time.Second)))
	claimed, _ := store.Claim(t.Context(), claimAt(now, ownerA, 10))
	if len(claimed) != 1 || claimed[0].Key != "MX:PRD-001" || string(claimed[0].Payload) != "{}" {
		t.Fatalf("unexpected claim %+v", claimed)
	}
	assertFencedAndReleased(t, store, now)
	later := now.Add(2 * time.Hour)
	again, _ := store.Claim(t.Context(), claimAt(later, ownerB, 10))
	if len(again) != 1 || again[0].Attempts != 1 {
		t.Fatalf("want reclaim after delay with attempts, got %+v", again)
	}
	if ok, err := store.MarkPublished(t.Context(), "evt-1", ownerB, later); !ok || err != nil {
		t.Fatalf("mark published: %v %v", ok, err)
	}
	if count, err := store.CountUnpublished(t.Context()); count != 0 || err != nil {
		t.Fatalf("want nothing pending, got %d %v", count, err)
	}
}

func assertFencedAndReleased(t *testing.T, store *Store, now time.Time) {
	t.Helper()
	if ok, err := store.MarkPublished(t.Context(), "evt-1", ownerB, now); ok || err != nil {
		t.Fatalf("other owner must be fenced off, got %v %v", ok, err)
	}
	if ok, err := store.Release(t.Context(), "evt-1", ownerA, 1, now.Add(time.Hour)); !ok ||
		err != nil {
		t.Fatalf("owner must release, got %v %v", ok, err)
	}
	if again, _ := store.Claim(t.Context(), claimAt(now, ownerB, 10)); len(again) != 0 {
		t.Fatal("released event must wait until availableAt")
	}
}

func TestExpiredLeaseIsReclaimed(t *testing.T) {
	store := openStore(t)
	now := time.Now().UTC().Truncate(time.Millisecond)
	insertEvents(t, store, pendingEvent("evt-1", "PE:PRD-009", 2, now.Add(-time.Second)))
	_, _ = store.Claim(t.Context(), claimAt(now, ownerA, 10))
	if during, _ := store.Claim(t.Context(), claimAt(now.Add(time.Second), ownerB,
		10)); len(during) != 0 {
		t.Fatal("active lease must not be stolen")
	}
	after, _ := store.Claim(t.Context(), claimAt(now.Add(lease+time.Second), ownerB, 10))
	if len(after) != 1 {
		t.Fatal("expired lease must be reclaimed")
	}
	if ok, _ := store.MarkPublished(t.Context(), "evt-1", ownerA, now); ok {
		t.Fatal("previous owner must lose the lease")
	}
}

func TestOutboxFailures(t *testing.T) {
	store := openStore(t)
	cancelled, cancel := context.WithCancel(context.Background())
	cancel()
	now := time.Now()
	if _, err := store.Claim(cancelled, claimAt(now, ownerA, 1)); err == nil {
		t.Fatal("want claim error")
	}
	if _, err := store.MarkPublished(cancelled, "x", ownerA, now); err == nil {
		t.Fatal("want mark error")
	}
	if _, err := store.Release(cancelled, "x", ownerA, 1, now); err == nil {
		t.Fatal("want release error")
	}
	if _, err := store.CountUnpublished(cancelled); err == nil {
		t.Fatal("want count error")
	}
}

func TestClaimReportsDecodeAndLeaseFailures(t *testing.T) {
	store := openStore(t)
	now := time.Now().UTC()
	_, err := store.outbox.InsertOne(t.Context(), bson.M{"_id": "bad", "key": "MX:PRD-001",
		"version": 1, "status": statusPending, "availableAt": now.Add(-time.Second),
		"createdAt": now, "attempts": "many"})
	if err != nil {
		t.Fatalf("insert: %v", err)
	}
	if _, err := store.Claim(t.Context(), claimAt(now, ownerA, 10)); err == nil {
		t.Fatal("want decode error")
	}
	other := openStore(t)
	insertEvents(t, other, pendingEvent("evt-1", "MX:PRD-001", 2, now.Add(-time.Second)))
	addValidator(t, other, outboxCollection, bson.M{"status": bson.M{"$ne": statusInFlight}})
	if _, err := other.Claim(t.Context(), claimAt(now, ownerA, 10)); err == nil {
		t.Fatal("want lease error")
	}
}

func TestUpdateEventIsClaimable(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	name := "Relay me"
	updated, err := store.Update(t.Context(), product.UpdateRequest{ID: "PRD-001", Market: "EC",
		Patch: product.Patch{Name: &name}, NewEvent: eventFor("evt-ec")})
	if err != nil {
		t.Fatalf("update: %v", err)
	}
	claimed, err := store.Claim(t.Context(), claimAt(time.Now().Add(time.Second), ownerA, 10))
	if err != nil || len(claimed) != 1 || claimed[0].Key != updated.EventKey() {
		t.Fatalf("want the change event claimable, got %+v err=%v", claimed, err)
	}
}

func TestLeaseOfVanishedCandidateIsSkipped(t *testing.T) {
	store := openStore(t)
	leased, err := store.lease(t.Context(), outboxDocument{ID: "gone"},
		claimAt(time.Now(), ownerA, 1))
	if err != nil || len(leased) != 0 {
		t.Fatalf("want nothing leased, got %+v err=%v", leased, err)
	}
}
