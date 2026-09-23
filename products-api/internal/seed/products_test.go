package seed_test

import (
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/product"
	"github.com/grupomariposa/platform/products-api/internal/seed"
)

func TestSeedRowsAreUniqueAndComplete(t *testing.T) {
	products := seed.Products()
	const wantRows = 24
	if len(products) != wantRows {
		t.Fatalf("want %d product-market rows, got %d", wantRows, len(products))
	}
	keys := map[string]bool{}
	for _, p := range products {
		if keys[p.EventKey()] || p.Version != product.InitialVersion || p.Name == "" ||
			p.SKU == "" {
			t.Fatalf("duplicated or incomplete seed row %+v", p)
		}
		keys[p.EventKey()] = true
	}
	for _, key := range []string{"CL:PRD-001", "EC:PRD-001", "MX:PRD-020", "CL:PRD-017"} {
		if !keys[key] {
			t.Fatalf("missing seed row %s", key)
		}
	}
}

func TestSeedCoversEveryMarket(t *testing.T) {
	perMarket := map[product.Market]int{}
	for _, p := range seed.Products() {
		perMarket[p.Market]++
	}
	want := map[product.Market]int{"MX": 8, "CO": 4, "PE": 5, "CL": 4, "EC": 3}
	for m, count := range want {
		if perMarket[m] != count {
			t.Fatalf("market %s: want %d rows, got %d", m, count, perMarket[m])
		}
	}
}
