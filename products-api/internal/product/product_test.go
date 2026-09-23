package product_test

import (
	"errors"
	"strings"
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
			if tc.wantErr != errors.Is(err, product.ErrInvalidID) {
				t.Fatalf("raw %q: unexpected error %v", tc.raw, err)
			}
			if !tc.wantErr && string(id) != tc.raw {
				t.Fatalf("want %q, got %q", tc.raw, id)
			}
		})
	}
}

func TestParseMarket(t *testing.T) {
	for _, raw := range []string{"MX", "CL", "ZZ"} {
		if got, err := product.ParseMarket(raw); err != nil || string(got) != raw {
			t.Fatalf("want %q, got %q err=%v", raw, got, err)
		}
	}
	for _, raw := range []string{"", "mx", "MXX", "M1"} {
		if _, err := product.ParseMarket(raw); !errors.Is(err, product.ErrInvalidMarket) {
			t.Fatalf("raw %q: want ErrInvalidMarket, got %v", raw, err)
		}
	}
}

func TestParseStatusAndTaxCategory(t *testing.T) {
	for _, raw := range []string{"ACTIVE", "DISCONTINUED"} {
		if got, err := product.ParseStatus(raw); err != nil || string(got) != raw {
			t.Fatalf("status %q: got %q err=%v", raw, got, err)
		}
	}
	for _, raw := range []string{"STANDARD", "REDUCED", "EXEMPT"} {
		if got, err := product.ParseTaxCategory(raw); err != nil || string(got) != raw {
			t.Fatalf("tax %q: got %q err=%v", raw, got, err)
		}
	}
	if _, err := product.ParseStatus("active"); !errors.Is(err, product.ErrInvalidStatus) {
		t.Fatalf("want ErrInvalidStatus, got %v", err)
	}
	if _, err := product.ParseTaxCategory("ZERO"); !errors.Is(err, product.ErrInvalidTaxCategory) {
		t.Fatalf("want ErrInvalidTaxCategory, got %v", err)
	}
}

func TestParseName(t *testing.T) {
	longest := strings.Repeat("ñ", product.MaxNameLength)
	for _, raw := range []string{"a", longest} {
		if got, err := product.ParseName(raw); err != nil || got != raw {
			t.Fatalf("name %q: got %q err=%v", raw, got, err)
		}
	}
	for _, raw := range []string{"", "   ", longest + "x"} {
		if _, err := product.ParseName(raw); !errors.Is(err, product.ErrInvalidName) {
			t.Fatalf("name %q: want ErrInvalidName, got %v", raw, err)
		}
	}
}

func TestApplyChangesOnlyGivenFieldsAndBumpsVersion(t *testing.T) {
	base := product.Product{ID: "PRD-001", Market: "MX", Name: "Old", SKU: "S",
		Status: product.StatusActive, TaxCategory: product.TaxStandard, Version: 3}
	name, status, tax := "New", product.StatusDiscontinued, product.TaxExempt
	cases := []struct {
		patch product.Patch
		want  product.Product
	}{
		{patch: product.Patch{Name: &name}, want: with(base, func(p *product.Product) {
			p.Name = name
		})},
		{patch: product.Patch{Status: &status}, want: with(base, func(p *product.Product) {
			p.Status = status
		})},
		{patch: product.Patch{TaxCategory: &tax}, want: with(base, func(p *product.Product) {
			p.TaxCategory = tax
		})},
	}
	for _, tc := range cases {
		if got, changed := base.Apply(tc.patch); !changed || got != tc.want {
			t.Fatalf("want %+v, got %+v changed=%v", tc.want, got, changed)
		}
	}
	if base.Version != 3 {
		t.Fatal("apply must not mutate the original")
	}
}

func TestApplyWithSameValuesIsNoOp(t *testing.T) {
	base := product.Product{ID: "PRD-001", Market: "MX", Name: "Same",
		Status: product.StatusActive, TaxCategory: product.TaxReduced, Version: 4}
	name, status, tax := base.Name, base.Status, base.TaxCategory
	for _, patch := range []product.Patch{{}, {Name: &name, Status: &status, TaxCategory: &tax}} {
		if got, changed := base.Apply(patch); changed || got != base {
			t.Fatalf("no-op patch must keep version, got %+v changed=%v", got, changed)
		}
	}
}

func with(p product.Product, mutate func(*product.Product)) product.Product {
	mutate(&p)
	p.Version++
	return p
}

func TestEventKeyAndPreconditions(t *testing.T) {
	current := product.Product{ID: "PRD-001", Market: "EC", Version: 2}
	if current.EventKey() != "EC:PRD-001" {
		t.Fatalf("unexpected key %q", current.EventKey())
	}
	cases := []struct {
		precondition *product.Precondition
		want         bool
	}{
		{precondition: nil, want: true},
		{precondition: &product.Precondition{StrongTags: []string{"1", "2"}}, want: true},
		{precondition: &product.Precondition{StrongTags: []string{"3", "abc"}}, want: false},
		{precondition: &product.Precondition{}, want: false},
	}
	for _, tc := range cases {
		request := product.UpdateRequest{Precondition: tc.precondition}
		if request.Matches(current) != tc.want {
			t.Fatalf("precondition %+v: want match %v", tc.precondition, tc.want)
		}
	}
}
