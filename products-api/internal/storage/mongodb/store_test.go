package mongodb

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	"github.com/grupomariposa/platform/products-api/internal/product"
	"github.com/grupomariposa/platform/products-api/internal/seed"
	"github.com/grupomariposa/platform/products-api/internal/testsupport/containers"
)

const (
	testTopic   = "products.changed.v1"
	testTimeout = 10 * time.Second
)

var (
	mongoURI  string
	databases atomic.Int32
)

func TestMain(m *testing.M) {
	os.Exit(run(m))
}

func run(m *testing.M) int {
	if !containers.Enabled() {
		return m.Run()
	}
	uri, stop, err := containers.StartMongo(context.Background())
	defer stop()
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	mongoURI = uri
	return m.Run()
}

func openStore(t *testing.T) *Store {
	t.Helper()
	if mongoURI == "" {
		t.Skip("integration tests disabled")
	}
	name := fmt.Sprintf("products_test_%d", databases.Add(1))
	store, err := Open(Settings{URI: mongoURI, Database: name, Topic: testTopic,
		Timeout: testTimeout})
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	t.Cleanup(func() {
		_ = store.products.Database().Drop(context.Background())
		_ = store.Close(context.Background())
	})
	return store
}

func setUp(t *testing.T, store *Store) {
	t.Helper()
	if err := store.Setup(t.Context(), seed.Products()); err != nil {
		t.Fatalf("setup: %v", err)
	}
}

func addValidator(t *testing.T, store *Store, collection string, rule bson.M) {
	t.Helper()
	db := store.products.Database()
	if err := db.CreateCollection(t.Context(), collection); err != nil &&
		!strings.Contains(err.Error(), "already exists") {
		t.Fatalf("create collection: %v", err)
	}
	command := bson.D{{Key: "collMod", Value: collection}, {Key: "validator", Value: rule}}
	if err := db.RunCommand(t.Context(), command).Err(); err != nil {
		t.Fatalf("collMod: %v", err)
	}
}

func eventFor(id string) func(product.Product) (product.ChangeEvent, error) {
	return func(p product.Product) (product.ChangeEvent, error) {
		return product.ChangeEvent{ID: id, Key: p.EventKey(),
			OccurredAt: time.Now().UTC().Truncate(time.Millisecond),
			Payload:    []byte(fmt.Sprintf(`{"version":%d}`, p.Version))}, nil
	}
}

func TestSetupSeedsOnceAndNeverOverwrites(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	name := "Changed by admin"
	if _, err := store.Update(t.Context(), product.UpdateRequest{ID: "PRD-001", Market: "CL",
		Patch: product.Patch{Name: &name}, NewEvent: eventFor("evt-1")}); err != nil {
		t.Fatalf("update: %v", err)
	}
	setUp(t, store)
	got, err := store.FindByIDInMarket(t.Context(), "PRD-001", "CL")
	if err != nil || got.Name != name || got.Version != 2 {
		t.Fatalf("seed must not overwrite changes, got %+v err=%v", got, err)
	}
	count, _ := store.products.CountDocuments(t.Context(), bson.M{})
	if count != int64(len(seed.Products())) {
		t.Fatalf("want %d seeded rows, got %d", len(seed.Products()), count)
	}
}

func TestUniqueProductMarketIndex(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	_, err := store.products.InsertOne(t.Context(), toDocument(product.Product{ID: "PRD-001",
		Market: "MX", Version: 1}, time.Now()))
	if !mongo.IsDuplicateKeyError(err) {
		t.Fatalf("want duplicate key error, got %v", err)
	}
}

func TestFindByIDInMarket(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	got, err := store.FindByIDInMarket(t.Context(), "PRD-018", "EC")
	want := product.Product{ID: "PRD-018", Market: "EC", Name: "Banano 1 kg",
		SKU: "BAN-1000-CAV", Status: product.StatusActive, TaxCategory: product.TaxExempt,
		Version: 1}
	if err != nil || got != want {
		t.Fatalf("want %+v, got %+v err=%v", want, got, err)
	}
	for _, market := range []product.Market{"MX", "US"} {
		if _, err := store.FindByIDInMarket(t.Context(), "PRD-018", market); !errors.Is(err,
			product.ErrNotFound) {
			t.Fatalf("market %s: want ErrNotFound, got %v", market, err)
		}
	}
}

func TestUpdateCommitsProductAndOutboxTogether(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	status := product.StatusDiscontinued
	updated, err := store.Update(t.Context(), product.UpdateRequest{ID: "PRD-020", Market: "MX",
		Patch: product.Patch{Status: &status}, ExpectedVersion: ptr(1),
		NewEvent: eventFor("0190a0b0-0000-7000-8000-000000000001")})
	if err != nil || updated.Status != status || updated.Version != 2 {
		t.Fatalf("unexpected update %+v err=%v", updated, err)
	}
	doc := assertOutboxDocument(t, store)
	var stored productDocument
	_ = store.products.FindOne(t.Context(), byKey("PRD-020", "MX")).Decode(&stored)
	if !stored.UpdatedAt.Equal(doc.CreatedAt) {
		t.Fatalf("updatedAt must match the event time, got %v vs %v", stored.UpdatedAt,
			doc.CreatedAt)
	}
}

func assertOutboxDocument(t *testing.T, store *Store) outboxDocument {
	t.Helper()
	var doc outboxDocument
	if err := store.outbox.FindOne(t.Context(), bson.M{}).Decode(&doc); err != nil {
		t.Fatalf("outbox: %v", err)
	}
	want := outboxDocument{ID: doc.ID, Topic: testTopic, Key: "MX:PRD-020",
		Payload: `{"version":2}`, ProductID: "PRD-020", Market: "MX", Version: 2,
		Status: statusPending, AvailableAt: doc.CreatedAt, CreatedAt: doc.CreatedAt}
	if doc.ID != "0190a0b0-0000-7000-8000-000000000001" || !equalOutbox(doc, want) {
		t.Fatalf("want %+v, got %+v", want, doc)
	}
	return doc
}

func equalOutbox(a, b outboxDocument) bool {
	return a.ID == b.ID && a.Topic == b.Topic && a.Key == b.Key && a.Payload == b.Payload &&
		a.ProductID == b.ProductID && a.Market == b.Market && a.Version == b.Version &&
		a.Status == b.Status && a.Attempts == b.Attempts && a.AvailableAt.Equal(b.AvailableAt)
}

func TestUpdateRejections(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	failing := func(product.Product) (product.ChangeEvent, error) {
		return product.ChangeEvent{}, errors.New("no entropy")
	}
	cases := []struct {
		name    string
		request product.UpdateRequest
		want    error
	}{
		{name: "should_reject_stale_version", want: product.ErrVersionConflict,
			request: product.UpdateRequest{ID: "PRD-001", Market: "MX", ExpectedVersion: ptr(5),
				NewEvent: eventFor("e1")}},
		{name: "should_reject_missing_product", want: product.ErrNotFound,
			request: product.UpdateRequest{ID: "PRD-999", Market: "MX", NewEvent: eventFor("e2")}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := store.Update(t.Context(), tc.request); !errors.Is(err, tc.want) {
				t.Fatalf("want %v, got %v", tc.want, err)
			}
		})
	}
	_, err := store.Update(t.Context(), product.UpdateRequest{ID: "PRD-001", Market: "MX",
		NewEvent: failing})
	assertNothingCommitted(t, store, err)
}

func TestUpdateRollsBackWhenOutboxInsertFails(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	addValidator(t, store, outboxCollection, bson.M{"topic": bson.M{"$ne": testTopic}})
	name := "never visible"
	_, err := store.Update(t.Context(), product.UpdateRequest{ID: "PRD-001", Market: "MX",
		Patch: product.Patch{Name: &name}, NewEvent: eventFor("evt-rollback")})
	assertNothingCommitted(t, store, err)
}

func TestUpdateFailsWhenProductWriteFails(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	addValidator(t, store, productsCollection, bson.M{"name": bson.M{"$ne": "REJECTED"}})
	name := "REJECTED"
	_, err := store.Update(t.Context(), product.UpdateRequest{ID: "PRD-001", Market: "MX",
		Patch: product.Patch{Name: &name}, NewEvent: eventFor("evt-reject")})
	assertNothingCommitted(t, store, err)
}

func assertNothingCommitted(t *testing.T, store *Store, err error) {
	t.Helper()
	if err == nil {
		t.Fatal("want update error")
	}
	got, _ := store.FindByIDInMarket(t.Context(), "PRD-001", "MX")
	count, _ := store.outbox.CountDocuments(t.Context(), bson.M{})
	if got.Version != product.InitialVersion || count != 0 {
		t.Fatalf("nothing must be committed, got version %d and %d events", got.Version, count)
	}
}

func TestConcurrentUpdatesWithSameVersionHaveOneWinner(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	const writers = 8
	var wins atomic.Int32
	var wg sync.WaitGroup
	for i := range writers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			name := fmt.Sprintf("writer-%d", i)
			_, err := store.Update(context.Background(), product.UpdateRequest{ID: "PRD-005",
				Market: "CO", Patch: product.Patch{Name: &name}, ExpectedVersion: ptr(1),
				NewEvent: eventFor(fmt.Sprintf("evt-%d", i))})
			if err == nil {
				wins.Add(1)
			}
		}()
	}
	wg.Wait()
	count, _ := store.outbox.CountDocuments(t.Context(), bson.M{})
	if wins.Load() != 1 || count != 1 {
		t.Fatalf("want one winner and one event, got %d winners and %d events", wins.Load(),
			count)
	}
}

func TestStoreFailures(t *testing.T) {
	store := openStore(t)
	cancelled, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := store.FindByIDInMarket(cancelled, "PRD-001", "MX"); err == nil ||
		errors.Is(err, product.ErrNotFound) {
		t.Fatalf("want find error, got %v", err)
	}
	if err := store.Setup(cancelled, seed.Products()); err == nil {
		t.Fatal("want index error")
	}
	if err := store.Ping(t.Context()); err != nil {
		t.Fatalf("ping: %v", err)
	}
	if err := store.Close(t.Context()); err != nil {
		t.Fatalf("close: %v", err)
	}
	if err := store.Close(t.Context()); err == nil {
		t.Fatal("want error closing twice")
	}
	if _, err := store.Update(t.Context(), product.UpdateRequest{}); err == nil {
		t.Fatal("want session error after close")
	}
}

func TestSeedFailure(t *testing.T) {
	store := openStore(t)
	addValidator(t, store, productsCollection, bson.M{"sku": bson.M{"$ne": "BEB-600-PET"}})
	if err := store.Setup(t.Context(), seed.Products()); err == nil {
		t.Fatal("want seed error")
	}
}

func TestOpenAndPingFailures(t *testing.T) {
	if _, err := Open(Settings{URI: "not-a-uri", Timeout: time.Second}); err == nil {
		t.Fatal("want connect error")
	}
	unreachable, err := Open(Settings{URI: "mongodb://127.0.0.1:1/?directConnection=true",
		Database: "x", Timeout: 200 * time.Millisecond})
	if err != nil {
		t.Fatalf("open: %v", err)
	}
	defer func() { _ = unreachable.Close(context.Background()) }()
	if err := unreachable.Ping(context.Background()); err == nil {
		t.Fatal("want ping error")
	}
}

func TestRequireMatched(t *testing.T) {
	if err := requireMatched(&mongo.UpdateResult{MatchedCount: 0}, nil); !errors.Is(err,
		product.ErrVersionConflict) {
		t.Fatalf("want version conflict, got %v", err)
	}
	if err := requireMatched(nil, errors.New("boom")); err == nil {
		t.Fatal("want update error")
	}
}

func TestIndexesCoverClaimQueries(t *testing.T) {
	store := openStore(t)
	setUp(t, store)
	cursor, err := store.outbox.Indexes().List(t.Context(), options.ListIndexes())
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	var indexes []bson.M
	_ = cursor.All(t.Context(), &indexes)
	const outboxIndexes = 4
	if len(indexes) != outboxIndexes {
		t.Fatalf("want %d outbox indexes including _id, got %d", outboxIndexes, len(indexes))
	}
}

func ptr(v int64) *int64 {
	return &v
}
