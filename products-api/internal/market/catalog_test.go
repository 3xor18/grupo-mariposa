package market_test

import (
	"errors"
	"slices"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/market"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

func TestParseDefaultCatalog(t *testing.T) {
	catalog, err := market.Parse(market.DefaultMarkets)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := []product.Market{"MX", "CO", "PE", "CL", "EC"}
	if !slices.Equal(catalog.Codes(), want) {
		t.Fatalf("want %v, got %v", want, catalog.Codes())
	}
	ecuador := catalog.Markets()[4]
	if ecuador != (market.Market{Code: "EC", Currency: "USD", Locale: "es-EC"}) {
		t.Fatalf("unexpected market %+v", ecuador)
	}
	if !catalog.Contains("CL") || catalog.Contains("US") {
		t.Fatal("contains must follow the catalog")
	}
}

func TestParseToleratesBlankEntries(t *testing.T) {
	catalog, err := market.Parse(" MX:MXN:es-MX , ,EC:USD:es-EC,")
	if err != nil || len(catalog.Codes()) != 2 {
		t.Fatalf("want two markets, got %v err=%v", catalog.Codes(), err)
	}
}

func TestParseRejectsInvalidCatalogs(t *testing.T) {
	cases := map[string]string{
		"should_reject_empty":          " , ",
		"should_reject_missing_parts":  "MX:MXN",
		"should_reject_bad_code":       "mx:MXN:es-MX",
		"should_reject_bad_currency":   "MX:MX:es-MX",
		"should_reject_bad_locale":     "MX:MXN:esMX",
		"should_reject_duplicate_code": "MX:MXN:es-MX,MX:USD:es-MX",
	}
	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := market.Parse(raw); !errors.Is(err, market.ErrInvalidCatalog) {
				t.Fatalf("want ErrInvalidCatalog, got %v", err)
			}
		})
	}
}
