package outbox_test

import (
	"bytes"
	"context"
	"errors"
	"log/slog"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/outbox"
)

var now = time.Date(2026, 9, 23, 10, 0, 0, 0, time.UTC)

type fakeStore struct {
	mu          sync.Mutex
	pending     []outbox.PendingEvent
	claims      []outbox.Claim
	published   []string
	released    map[string]int
	availableAt time.Time
	claimErr    error
	markErr     error
	markLost    bool
	releaseErr  error
	countErr    error
	cycles      chan struct{}
}

func (f *fakeStore) Claim(_ context.Context, claim outbox.Claim) ([]outbox.PendingEvent, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.claims = append(f.claims, claim)
	if f.cycles != nil {
		select {
		case f.cycles <- struct{}{}:
		default:
		}
	}
	batch := f.pending
	f.pending = nil
	return batch, f.claimErr
}

func (f *fakeStore) MarkPublished(_ context.Context, id, _ string, _ time.Time) (bool, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.markErr != nil || f.markLost {
		return false, f.markErr
	}
	f.published = append(f.published, id)
	return true, nil
}

func (f *fakeStore) Release(_ context.Context, id, _ string, attempts int, at time.Time,
) (bool, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.released == nil {
		f.released = map[string]int{}
	}
	f.released[id] = attempts
	f.availableAt = at
	return f.releaseErr == nil, f.releaseErr
}

func (f *fakeStore) Backlog(context.Context) (outbox.Backlog, error) {
	return outbox.Backlog{Pending: int64(len(f.released)), Oldest: now.Add(-time.Minute)},
		f.countErr
}

type fakePublisher struct {
	failing  map[string]bool
	messages []outbox.Message
	deadline bool
}

func (p *fakePublisher) Publish(ctx context.Context, messages []outbox.Message) []error {
	_, p.deadline = ctx.Deadline()
	p.messages = append(p.messages, messages...)
	results := make([]error, len(messages))
	for i, m := range messages {
		if p.failing[string(m.Key)] {
			results[i] = errors.New("broker unavailable")
		}
	}
	return results
}

type fakeRecorder struct {
	pending   int64
	oldestAge time.Duration
	published int
	failed    int
}

func (r *fakeRecorder) OutboxBacklog(count int64, age time.Duration) {
	r.pending, r.oldestAge = count, age
}
func (r *fakeRecorder) OutboxPublished(count int) { r.published += count }
func (r *fakeRecorder) OutboxFailed(count int)    { r.failed += count }

func settings() outbox.Settings {
	return outbox.Settings{Interval: time.Millisecond, BatchSize: 10, Lease: 30 * time.Second,
		RetryDelay: 5 * time.Second, Owner: "relay-1"}
}

func newRelay(store *fakeStore, publisher *fakePublisher, recorder *fakeRecorder,
	logs *bytes.Buffer,
) *outbox.Relay {
	return outbox.NewRelay(store, publisher, recorder, settings(), func() time.Time { return now },
		slog.New(slog.NewJSONHandler(logs, nil)))
}

func events(keys ...string) []outbox.PendingEvent {
	batch := make([]outbox.PendingEvent, 0, len(keys))
	for i, key := range keys {
		batch = append(batch, outbox.PendingEvent{ID: "evt-" + key, Key: key,
			Payload: []byte(`{}`), Attempts: i})
	}
	return batch
}

func TestCyclePublishesAndReleasesFailures(t *testing.T) {
	store := &fakeStore{pending: events("MX:PRD-001", "CL:PRD-015")}
	publisher := &fakePublisher{failing: map[string]bool{"CL:PRD-015": true}}
	recorder := &fakeRecorder{}
	published, err := newRelay(store, publisher, recorder, &bytes.Buffer{}).Cycle(t.Context())
	if err != nil || published != 1 {
		t.Fatalf("want one published, got %d err=%v", published, err)
	}
	assertClaim(t, store.claims[0])
	if len(store.published) != 1 || store.published[0] != "evt-MX:PRD-001" {
		t.Fatalf("unexpected published %v", store.published)
	}
	if store.released["evt-CL:PRD-015"] != 2 || !store.availableAt.Equal(now.Add(5*time.Second)) {
		t.Fatalf("failure must be released with attempts+1 and delay, got %v %v",
			store.released, store.availableAt)
	}
	assertCycleSideEffects(t, recorder, publisher)
}

func assertClaim(t *testing.T, claim outbox.Claim) {
	t.Helper()
	want := outbox.Claim{Limit: 10, Now: now, LeaseUntil: now.Add(30 * time.Second),
		Owner: "relay-1"}
	if claim != want {
		t.Fatalf("want claim %+v, got %+v", want, claim)
	}
}

func assertCycleSideEffects(t *testing.T, recorder *fakeRecorder, publisher *fakePublisher) {
	t.Helper()
	if *recorder != (fakeRecorder{pending: 1, oldestAge: time.Minute, published: 1, failed: 1}) {
		t.Fatalf("unexpected metrics %+v", recorder)
	}
	if string(publisher.messages[0].Key) != "MX:PRD-001" || !publisher.deadline {
		t.Fatal("messages must carry the key and a bounded deadline")
	}
}

func TestCycleWithNothingToPublish(t *testing.T) {
	store, publisher, recorder := &fakeStore{}, &fakePublisher{}, &fakeRecorder{}
	published, err := newRelay(store, publisher, recorder, &bytes.Buffer{}).Cycle(t.Context())
	if err != nil || published != 0 || len(publisher.messages) != 0 || recorder.oldestAge != 0 {
		t.Fatalf("want idle cycle with no backlog age, got %d err=%v %+v", published, err,
			recorder)
	}
}

func TestCycleErrors(t *testing.T) {
	cases := map[string]*fakeStore{
		"claim": {claimErr: errors.New("mongo down")},
		"count": {pending: events("MX:PRD-001"), countErr: errors.New("mongo down")},
	}
	for name, store := range cases {
		t.Run(name, func(t *testing.T) {
			_, err := newRelay(store, &fakePublisher{}, &fakeRecorder{}, &bytes.Buffer{}).
				Cycle(t.Context())
			if err == nil {
				t.Fatal("want cycle error")
			}
		})
	}
}

func TestLostLeaseIsNotCountedAndStoreFailuresAreLogged(t *testing.T) {
	var logs bytes.Buffer
	store := &fakeStore{pending: events("MX:PRD-001", "MX:PRD-002"), markLost: true,
		releaseErr: errors.New("write conflict")}
	publisher := &fakePublisher{failing: map[string]bool{"MX:PRD-002": true}}
	recorder := &fakeRecorder{}
	published, _ := newRelay(store, publisher, recorder, &logs).Cycle(t.Context())
	if published != 0 || recorder.failed != 1 {
		t.Fatalf("lost lease must not count as published, got %d %+v", published, recorder)
	}
	if !strings.Contains(logs.String(), "could not be released") {
		t.Fatalf("release failure must be logged, got %s", logs.String())
	}
	store = &fakeStore{pending: events("MX:PRD-001"), markErr: errors.New("timeout")}
	logs.Reset()
	_, _ = newRelay(store, &fakePublisher{}, &fakeRecorder{}, &logs).Cycle(t.Context())
	if !strings.Contains(logs.String(), "could not be confirmed") {
		t.Fatalf("confirm failure must be logged, got %s", logs.String())
	}
}

func TestRunLoopsUntilCancelled(t *testing.T) {
	var logs bytes.Buffer
	store := &fakeStore{claimErr: errors.New("mongo down"), cycles: make(chan struct{}, 1)}
	relay := newRelay(store, &fakePublisher{}, &fakeRecorder{}, &logs)
	ctx, cancel := context.WithCancel(t.Context())
	done := make(chan struct{})
	go func() {
		relay.Run(ctx)
		close(done)
	}()
	for range 3 {
		select {
		case <-store.cycles:
		case <-time.After(5 * time.Second):
			t.Fatal("relay did not keep cycling")
		}
	}
	cancel()
	<-done
	if !strings.Contains(logs.String(), "outbox relay cycle failed") {
		t.Fatalf("cycle failures must be logged, got %s", logs.String())
	}
}
