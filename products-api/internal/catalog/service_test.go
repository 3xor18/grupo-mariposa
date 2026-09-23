package catalog_test

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"testing"
	"time"

	"github.com/getkin/kin-openapi/openapi3"

	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/market"
	"github.com/grupomariposa/platform/products-api/internal/product"
	"github.com/grupomariposa/platform/products-api/internal/seed"
	"github.com/grupomariposa/platform/products-api/internal/storage/memory"
)

var fixedTime = time.Date(2026, 9, 23, 12, 0, 0, 0, time.FixedZone("CO", -5*3600))

type stubRepository struct {
	found   product.Product
	err     error
	calls   int
	request product.UpdateRequest
}

func (s *stubRepository) FindByIDInMarket(
	_ context.Context, _ product.ID, _ product.Market,
) (product.Product, error) {
	s.calls++
	return s.found, s.err
}

func (s *stubRepository) Update(_ context.Context, r product.UpdateRequest,
) (product.Product, error) {
	s.calls++
	s.request = r
	return s.found, s.err
}

func catalogOf(t *testing.T) market.Catalog {
	t.Helper()
	markets, err := market.Parse(market.DefaultMarkets, market.DefaultCurrencies)
	if err != nil {
		t.Fatalf("catalog: %v", err)
	}
	return markets
}

func events() *catalog.ChangeEvents {
	return catalog.NewChangeEvents(func() time.Time { return fixedTime },
		func() (string, error) { return "0190a0b0-0000-7000-8000-000000000001", nil })
}

func newService(t *testing.T, repo *stubRepository) *catalog.Service {
	t.Helper()
	return catalog.NewService(repo, repo, catalogOf(t), events())
}

func TestGetProductReturnsProduct(t *testing.T) {
	want := product.Product{ID: "PRD-001", Market: "CL", Name: "Bebida", Version: 1}
	repo := &stubRepository{found: want}
	got, err := newService(t, repo).GetProduct(context.Background(),
		catalog.Query{ProductID: "PRD-001", Market: "CL"})
	if err != nil || got != want {
		t.Fatalf("want %+v, got %+v err=%v", want, got, err)
	}
}

func TestGetProductWrapsRepositoryError(t *testing.T) {
	repo := &stubRepository{err: product.ErrNotFound}
	_, err := newService(t, repo).GetProduct(context.Background(),
		catalog.Query{ProductID: "PRD-001", Market: "PE"})
	if !errors.Is(err, product.ErrNotFound) {
		t.Fatalf("want ErrNotFound, got %v", err)
	}
}

func TestGetProductValidation(t *testing.T) {
	cases := []struct {
		name  string
		query catalog.Query
		want  []catalog.Violation
	}{
		{name: "should_reject_invalid_id", query: catalog.Query{ProductID: "bad", Market: "MX"},
			want: []catalog.Violation{{Field: "productId", Message: "must match ^PRD-[A-Z0-9]{1,20}$"}}},
		{name: "should_require_market", query: catalog.Query{ProductID: "PRD-001"},
			want: []catalog.Violation{{Field: "market", Message: "is required"}}},
		{name: "should_reject_market_outside_catalog",
			query: catalog.Query{ProductID: "PRD-001", Market: "US"},
			want: []catalog.Violation{{Field: "market",
				Message: "must be one of MX, CO, PE, CL, EC"}}},
		{name: "should_report_all_violations", query: catalog.Query{ProductID: "x", Market: "mx"},
			want: []catalog.Violation{
				{Field: "productId", Message: "must match ^PRD-[A-Z0-9]{1,20}$"},
				{Field: "market", Message: "must be one of MX, CO, PE, CL, EC"},
			}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			repo := &stubRepository{}
			_, err := newService(t, repo).GetProduct(context.Background(), tc.query)
			assertViolations(t, err, tc.want)
			if repo.calls != 0 {
				t.Fatal("repository must not be called on invalid input")
			}
		})
	}
}

func TestUpdateProductBuildsRequest(t *testing.T) {
	updated := product.Product{ID: "PRD-001", Market: "EC", Version: 4}
	repo := &stubRepository{found: updated}
	name, status, tax, version := "Nuevo", "DISCONTINUED", "EXEMPT", "3"
	got, err := newService(t, repo).UpdateProduct(context.Background(), catalog.UpdateCommand{
		ProductID: "PRD-001", Market: "EC", Name: &name, Status: &status, TaxCategory: &tax,
		Precondition: &product.Precondition{StrongTags: []string{version}},
	})
	if err != nil || got != updated {
		t.Fatalf("want %+v, got %+v err=%v", updated, got, err)
	}
	r := repo.request
	if r.ID != "PRD-001" || r.Market != "EC" || *r.Patch.Name != name ||
		*r.Patch.Status != product.StatusDiscontinued || *r.Patch.TaxCategory != product.TaxExempt ||
		r.Precondition.StrongTags[0] != version || r.NewEvent == nil {
		t.Fatalf("unexpected request %+v", r)
	}
}

func TestUpdateProductWrapsWriterErrors(t *testing.T) {
	repo := &stubRepository{err: product.ErrVersionConflict}
	status := "ACTIVE"
	_, err := newService(t, repo).UpdateProduct(context.Background(), catalog.UpdateCommand{
		ProductID: "PRD-001", Market: "MX", Status: &status})
	if !errors.Is(err, product.ErrVersionConflict) {
		t.Fatalf("want ErrVersionConflict, got %v", err)
	}
}

func TestUpdateProductValidation(t *testing.T) {
	bad, blank := "BAD", " "
	cases := []struct {
		name string
		cmd  catalog.UpdateCommand
		want []catalog.Violation
	}{
		{name: "should_require_a_field",
			cmd: catalog.UpdateCommand{ProductID: "PRD-001", Market: "MX"},
			want: []catalog.Violation{{Field: "body",
				Message: "must contain at least one of name, status, taxCategory"}}},
		{name: "should_reject_every_invalid_field",
			cmd: catalog.UpdateCommand{ProductID: "PRD-001", Market: "MX", Name: &blank,
				Status: &bad, TaxCategory: &bad},
			want: []catalog.Violation{
				{Field: "name", Message: "must have between 1 and 120 characters"},
				{Field: "status", Message: "must be one of ACTIVE, DISCONTINUED"},
				{Field: "taxCategory", Message: "must be one of STANDARD, REDUCED, EXEMPT"},
			}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			repo := &stubRepository{}
			_, err := newService(t, repo).UpdateProduct(context.Background(), tc.cmd)
			assertViolations(t, err, tc.want)
			if repo.calls != 0 {
				t.Fatal("writer must not be called on invalid input")
			}
		})
	}
}

func TestUpdateProductEndToEndWithMemoryAdapter(t *testing.T) {
	repo := memory.NewRepository(seed.Products())
	service := catalog.NewService(repo, repo, catalogOf(t), events())
	tax := "REDUCED"
	updated, err := service.UpdateProduct(context.Background(), catalog.UpdateCommand{
		ProductID: "PRD-018", Market: "EC", TaxCategory: &tax})
	if err != nil || updated.TaxCategory != product.TaxReduced || updated.Version != 2 {
		t.Fatalf("unexpected update %+v err=%v", updated, err)
	}
}

func TestChangeEventPayload(t *testing.T) {
	p := product.Product{ID: "PRD-001", Market: "CL", Name: "Bebida 600 ml", SKU: "BEB",
		Status: product.StatusActive, TaxCategory: product.TaxStandard, Version: 2}
	event, err := events().Build(p)
	if err != nil {
		t.Fatalf("build: %v", err)
	}
	var payload map[string]any
	if err := json.Unmarshal(event.Payload, &payload); err != nil {
		t.Fatalf("decode: %v", err)
	}
	want := map[string]any{
		"eventId": "0190a0b0-0000-7000-8000-000000000001", "occurredAt": "2026-09-23T17:00:00.000Z",
		"productId": "PRD-001", "market": "CL", "version": float64(2), "status": "ACTIVE",
		"taxCategory": "STANDARD", "name": "Bebida 600 ml", "sku": "BEB",
	}
	for k, v := range want {
		if payload[k] != v {
			t.Fatalf("%s: want %v, got %v", k, v, payload[k])
		}
	}
	if event.ID != want["eventId"] || event.Key != "CL:PRD-001" ||
		!event.OccurredAt.Equal(fixedTime) {
		t.Fatalf("unexpected event envelope %+v", event)
	}
}

func TestChangeEventFailsWithoutID(t *testing.T) {
	failing := catalog.NewChangeEvents(time.Now, func() (string, error) {
		return "", errors.New("entropy exhausted")
	})
	if _, err := failing.Build(product.Product{}); err == nil {
		t.Fatal("want id generation error")
	}
}

func assertViolations(t *testing.T, err error, want []catalog.Violation) {
	t.Helper()
	var validationErr *catalog.ValidationError
	if !errors.As(err, &validationErr) || validationErr.Error() == "" {
		t.Fatalf("want ValidationError, got %v", err)
	}
	if len(validationErr.Violations) != len(want) {
		t.Fatalf("want %+v, got %+v", want, validationErr.Violations)
	}
	for i := range want {
		if validationErr.Violations[i] != want[i] {
			t.Fatalf("violation %d: want %+v, got %+v", i, want[i], validationErr.Violations[i])
		}
	}
}

func TestChangeEventMatchesContract(t *testing.T) {
	raw, err := os.ReadFile("../../../contracts/events/products.changed.v1.schema.json")
	if err != nil {
		t.Fatalf("read schema: %v", err)
	}
	var schema openapi3.Schema
	if err := json.Unmarshal(raw, &schema); err != nil {
		t.Fatalf("decode schema: %v", err)
	}
	for _, p := range seed.Products() {
		event, err := events().Build(p)
		if err != nil {
			t.Fatalf("build: %v", err)
		}
		var value any
		_ = json.Unmarshal(event.Payload, &value)
		if err := schema.VisitJSON(value, openapi3.EnableFormatValidation()); err != nil {
			t.Fatalf("%s violates products.changed.v1: %v", p.EventKey(), err)
		}
	}
	broken := map[string]any{"eventId": "x", "occurredAt": "yesterday", "productId": "PRD-1",
		"market": "mx", "version": 0, "status": "GONE", "taxCategory": "ZERO"}
	if schema.VisitJSON(broken, openapi3.EnableFormatValidation()) == nil {
		t.Fatal("schema validation must reject an invalid event")
	}
}
