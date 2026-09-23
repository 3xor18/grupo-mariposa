package market_test

import (
	"errors"
	"slices"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/market"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

func TestParseDefaultCatalog(t *testing.T) {
	catalog, err := market.Parse(market.DefaultMarkets, market.DefaultCurrencies)
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
	chile := market.Currency{Code: "CLP", Digits: 0}
	if !slices.Contains(catalog.Currencies(), chile) || len(catalog.Currencies()) != 5 {
		t.Fatalf("unexpected currencies %+v", catalog.Currencies())
	}
	if !catalog.Contains("CL") || catalog.Contains("US") {
		t.Fatal("contains must follow the catalog")
	}
}

func TestParseTrimsEntries(t *testing.T) {
	catalog, err := market.Parse(" MX:MXN:es-MX , ,EC:USD:es-EC,", " MXN:2 , USD:4 ")
	if err != nil || len(catalog.Codes()) != 2 {
		t.Fatalf("want two markets, got %v err=%v", catalog.Codes(), err)
	}
}

func TestParseRejectsInvalidCatalogs(t *testing.T) {
	const currencies = "MXN:2,USD:2"
	cases := []struct {
		name, markets, currencies string
	}{
		{"should_reject_empty_markets", " , ", currencies},
		{"should_reject_missing_parts", "MX:MXN", currencies},
		{"should_reject_bad_code", "mx:MXN:es-MX", currencies},
		{"should_reject_bad_currency", "MX:MX:es-MX", currencies},
		{"should_reject_bad_locale", "MX:MXN:esMX", currencies},
		{"should_reject_duplicate_code", "MX:MXN:es-MX,MX:USD:es-MX", currencies},
		{"should_reject_undeclared_currency", "PE:PEN:es-PE", currencies},
		{"should_reject_empty_currencies", "MX:MXN:es-MX", ""},
		{"should_reject_currency_without_digits", "MX:MXN:es-MX", "MXN"},
		{"should_reject_lowercase_currency", "MX:MXN:es-MX", "mxn:2"},
		{"should_reject_too_many_digits", "MX:MXN:es-MX", "MXN:5"},
		{"should_reject_negative_digits", "MX:MXN:es-MX", "MXN:-1"},
		{"should_reject_duplicate_currency", "MX:MXN:es-MX", "MXN:2,MXN:0"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := market.Parse(tc.markets, tc.currencies); !errors.Is(err,
				market.ErrInvalidCatalog) {
				t.Fatalf("want ErrInvalidCatalog, got %v", err)
			}
		})
	}
}
