package memory_test

import (
	"context"
	"errors"
	"strconv"
	"sync"
	"sync/atomic"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/product"
	"github.com/grupomariposa/platform/products-api/internal/seed"
	"github.com/grupomariposa/platform/products-api/internal/storage/memory"
)

func noEvent(product.Product) (product.ChangeEvent, error) {
	return product.ChangeEvent{}, nil
}

func TestFindByIDInMarket(t *testing.T) {
	repo := memory.NewRepository(seed.Products())
	cases := []struct {
		name    string
		id      product.ID
		market  product.Market
		wantSKU string
		wantErr error
	}{
		{name: "should_find_in_mx", id: "PRD-001", market: "MX", wantSKU: "BEB-600-PET"},
		{name: "should_find_in_ec", id: "PRD-001", market: "EC", wantSKU: "BEB-600-PET"},
		{name: "should_not_find_unknown", id: "PRD-999", market: "MX",
			wantErr: product.ErrNotFound},
		{name: "should_not_find_in_other_market", id: "PRD-002", market: "PE",
			wantErr: product.ErrNotFound},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, err := repo.FindByIDInMarket(context.Background(), tc.id, tc.market)
			if !errors.Is(err, tc.wantErr) || got.SKU != tc.wantSKU {
				t.Fatalf("want %q/%v, got %q/%v", tc.wantSKU, tc.wantErr, got.SKU, err)
			}
		})
	}
}

func TestUpdateAppliesPatchAndBuildsEvent(t *testing.T) {
	repo := memory.NewRepository(seed.Products())
	status := product.StatusDiscontinued
	var event product.Product
	updated, err := repo.Update(context.Background(), product.UpdateRequest{
		ID: "PRD-020", Market: "MX", Patch: product.Patch{Status: &status},
		Precondition: at(product.InitialVersion),
		NewEvent: func(p product.Product) (product.ChangeEvent, error) {
			event = p
			return product.ChangeEvent{}, nil
		},
	})
	if err != nil || updated.Status != status || updated.Version != 2 || event != updated {
		t.Fatalf("unexpected update %+v err=%v event=%+v", updated, err, event)
	}
	stored, _ := repo.FindByIDInMarket(context.Background(), "PRD-020", "MX")
	if stored != updated {
		t.Fatalf("update must be persisted, got %+v", stored)
	}
}

func TestUpdateFailures(t *testing.T) {
	cancelled, cancel := context.WithCancel(context.Background())
	cancel()
	failingEvent := func(product.Product) (product.ChangeEvent, error) {
		return product.ChangeEvent{}, errors.New("no entropy")
	}
	cases := []struct {
		name    string
		ctx     context.Context
		request product.UpdateRequest
		want    error
	}{
		{name: "should_fail_when_cancelled", ctx: cancelled, want: context.Canceled,
			request: product.UpdateRequest{ID: "PRD-001", Market: "MX", NewEvent: noEvent}},
		{name: "should_fail_when_missing", ctx: context.Background(), want: product.ErrNotFound,
			request: product.UpdateRequest{ID: "PRD-001", Market: "US", NewEvent: noEvent}},
		{name: "should_fail_when_stale", ctx: context.Background(),
			want: product.ErrVersionConflict, request: product.UpdateRequest{ID: "PRD-001",
				Market: "MX", Precondition: at(7), NewEvent: noEvent}},
	}
	repo := memory.NewRepository(seed.Products())
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := repo.Update(tc.ctx, tc.request); !errors.Is(err, tc.want) {
				t.Fatalf("want %v, got %v", tc.want, err)
			}
		})
	}
	name := "renamed"
	_, err := repo.Update(context.Background(), product.UpdateRequest{ID: "PRD-001",
		Market: "MX", Patch: product.Patch{Name: &name}, NewEvent: failingEvent})
	stored, _ := repo.FindByIDInMarket(context.Background(), "PRD-001", "MX")
	if err == nil || stored.Version != product.InitialVersion {
		t.Fatalf("event failure must abort the update, got %v version %d", err, stored.Version)
	}
}

func TestFindHonoursCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := memory.NewRepository(seed.Products()).FindByIDInMarket(ctx, "PRD-001", "MX")
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("want context.Canceled, got %v", err)
	}
}

func TestConcurrentUpdatesWithSameVersionHaveOneWinner(t *testing.T) {
	const writers = 32
	repo := memory.NewRepository(seed.Products())
	name := "Concurrent"
	var wins atomic.Int32
	var wg sync.WaitGroup
	for range writers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			_, err := repo.Update(context.Background(), product.UpdateRequest{ID: "PRD-001",
				Market: "PE", Patch: product.Patch{Name: &name},
				Precondition: at(product.InitialVersion), NewEvent: noEvent})
			if err == nil {
				wins.Add(1)
			}
		}()
	}
	wg.Wait()
	if wins.Load() != 1 {
		t.Fatalf("want exactly one winner, got %d", wins.Load())
	}
}

func at(version int64) *product.Precondition {
	return &product.Precondition{StrongTags: []string{strconv.FormatInt(version, 10)}}
}

func TestNoOpUpdateKeepsVersionAndSkipsEvent(t *testing.T) {
	repo := memory.NewRepository(seed.Products())
	current, _ := repo.FindByIDInMarket(context.Background(), "PRD-001", "MX")
	name := current.Name
	got, err := repo.Update(context.Background(), product.UpdateRequest{ID: "PRD-001",
		Market: "MX", Patch: product.Patch{Name: &name}, Precondition: at(1),
		NewEvent: func(product.Product) (product.ChangeEvent, error) {
			t.Fatal("no-op must not build an event")
			return product.ChangeEvent{}, nil
		}})
	if err != nil || got != current {
		t.Fatalf("want unchanged %+v, got %+v err=%v", current, got, err)
	}
	if _, err := repo.Update(context.Background(), product.UpdateRequest{ID: "PRD-001",
		Market: "MX", Patch: product.Patch{Name: &name}, Precondition: at(9),
		NewEvent: noEvent}); !errors.Is(err, product.ErrVersionConflict) {
		t.Fatalf("stale precondition must still fail on no-op, got %v", err)
	}
}
