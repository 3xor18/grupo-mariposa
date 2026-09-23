package memory_test

import (
	"context"
	"errors"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/product"
	"github.com/grupomariposa/platform/products-api/internal/storage/memory"
)

func TestFindByIDInMarket(t *testing.T) {
	repo := memory.NewSeededRepository()
	cases := []struct {
		name    string
		id      product.ID
		market  product.Market
		wantSKU string
		wantErr error
	}{
		{name: "should_find_multi_market_in_mx", id: "PRD-001", market: product.MarketMX,
			wantSKU: "BEB-600-PET"},
		{name: "should_find_multi_market_in_pe", id: "PRD-001", market: product.MarketPE,
			wantSKU: "BEB-600-PET"},
		{name: "should_find_discontinued", id: "PRD-007", market: product.MarketCO,
			wantSKU: "PAN-500-BLQ"},
		{name: "should_not_find_unknown", id: "PRD-999", market: product.MarketMX,
			wantErr: product.ErrNotFound},
		{name: "should_not_find_in_other_market", id: "PRD-002", market: product.MarketPE,
			wantErr: product.ErrNotFound},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, err := repo.FindByIDInMarket(context.Background(), tc.id, tc.market)
			if !errors.Is(err, tc.wantErr) {
				t.Fatalf("want %v, got %v", tc.wantErr, err)
			}
			if got.SKU != tc.wantSKU {
				t.Fatalf("want sku %q, got %q", tc.wantSKU, got.SKU)
			}
		})
	}
}

func TestFindByIDInMarketHonoursCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := memory.NewSeededRepository().FindByIDInMarket(ctx, "PRD-001", product.MarketMX)
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("want context.Canceled, got %v", err)
	}
}

func TestSeedCoversAllMarkets(t *testing.T) {
	const minimumProducts = 10
	seed := memory.Seed()
	if len(seed) < minimumProducts {
		t.Fatalf("want at least %d products, got %d", minimumProducts, len(seed))
	}
	perMarket := map[product.Market]int{}
	for _, l := range seed {
		for _, m := range l.Markets {
			perMarket[m]++
		}
	}
	for _, m := range product.Markets() {
		if perMarket[m] == 0 {
			t.Fatalf("market %s has no products", m)
		}
	}
}
