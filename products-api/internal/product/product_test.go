package product_test

import (
	"errors"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

func TestParseID(t *testing.T) {
	cases := []struct {
		name    string
		raw     string
		wantErr bool
	}{
		{name: "should_accept_when_pattern_matches", raw: "PRD-001"},
		{name: "should_accept_when_twenty_chars", raw: "PRD-ABCDEFGHIJ0123456789"},
		{name: "should_reject_when_empty", raw: "", wantErr: true},
		{name: "should_reject_when_lowercase", raw: "prd-001", wantErr: true},
		{name: "should_reject_when_suffix_empty", raw: "PRD-", wantErr: true},
		{name: "should_reject_when_too_long", raw: "PRD-ABCDEFGHIJ01234567890", wantErr: true},
		{name: "should_reject_when_symbols", raw: "PRD-00$", wantErr: true},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			id, err := product.ParseID(tc.raw)
			if tc.wantErr {
				if !errors.Is(err, product.ErrInvalidID) {
					t.Fatalf("want ErrInvalidID, got %v", err)
				}
				return
			}
			if err != nil || string(id) != tc.raw {
				t.Fatalf("want %q, got %q err=%v", tc.raw, id, err)
			}
		})
	}
}

func TestParseMarket(t *testing.T) {
	for _, market := range product.Markets() {
		got, err := product.ParseMarket(string(market))
		if err != nil || got != market {
			t.Fatalf("want %q, got %q err=%v", market, got, err)
		}
	}
	for _, raw := range []string{"", "mx", "US", "MXX"} {
		if _, err := product.ParseMarket(raw); !errors.Is(err, product.ErrInvalidMarket) {
			t.Fatalf("raw %q: want ErrInvalidMarket, got %v", raw, err)
		}
	}
}
