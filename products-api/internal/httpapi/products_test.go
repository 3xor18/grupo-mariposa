package httpapi_test

import (
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

func TestGetProductReturnsProduct(t *testing.T) {
	h := newHarness(t)
	rec := h.get(t, "/products/PRD-001?market=PE")
	if rec.Code != http.StatusOK || rec.Header().Get("Content-Type") != "application/json" {
		t.Fatalf("unexpected response %d %s", rec.Code, rec.Header().Get("Content-Type"))
	}
	var body map[string]string
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	want := map[string]string{
		"productId": "PRD-001", "name": "Bebida 600 ml", "sku": "BEB-600-PET",
		"status": "ACTIVE", "taxCategory": "STANDARD",
	}
	for key, value := range want {
		if body[key] != value {
			t.Fatalf("field %s: want %q, got %q", key, value, body[key])
		}
	}
}

func TestGetProductValidation(t *testing.T) {
	cases := []struct {
		name   string
		target string
		fields []string
	}{
		{name: "should_reject_bad_id", target: "/products/ABC?market=MX",
			fields: []string{"productId"}},
		{name: "should_require_market", target: "/products/PRD-001", fields: []string{"market"}},
		{name: "should_reject_unknown_market", target: "/products/PRD-001?market=US",
			fields: []string{"market"}},
		{name: "should_report_every_field", target: "/products/prd-1?market=mx",
			fields: []string{"productId", "market"}},
	}
	h := newHarness(t)
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := h.get(t, tc.target)
			assertProblem(t, rec, http.StatusBadRequest, httpapi.CodeValidation)
			problem := decodeProblem(t, rec)
			if len(problem.Errors) != len(tc.fields) {
				t.Fatalf("want %d errors, got %+v", len(tc.fields), problem.Errors)
			}
			for i, field := range tc.fields {
				if problem.Errors[i].Field != field || problem.Errors[i].Message == "" {
					t.Fatalf("error %d: want field %s, got %+v", i, field, problem.Errors[i])
				}
			}
		})
	}
}

func TestGetProductNotFound(t *testing.T) {
	h := newHarness(t)
	for _, target := range []string{"/products/PRD-999?market=MX", "/products/PRD-002?market=CO"} {
		rec := h.get(t, target)
		assertProblem(t, rec, http.StatusNotFound, httpapi.CodeProductNotFound)
		problem := decodeProblem(t, rec)
		if !strings.HasPrefix(target, problem.Instance) || len(problem.Errors) != 0 {
			t.Fatalf("unexpected problem %+v", problem)
		}
		if problem.Type != "https://contracts.grupomariposa.dev/problems/product-not-found" {
			t.Fatalf("unexpected type %q", problem.Type)
		}
	}
}

func TestGetProductMapsFinderErrors(t *testing.T) {
	cases := []struct {
		name   string
		err    error
		status int
		code   httpapi.Code
	}{
		{name: "should_map_deadline", err: context.DeadlineExceeded,
			status: http.StatusServiceUnavailable, code: httpapi.CodeServiceUnavailable},
		{name: "should_map_cancel", err: context.Canceled,
			status: http.StatusServiceUnavailable, code: httpapi.CodeServiceUnavailable},
		{name: "should_map_unexpected", err: errors.New("disk on fire"),
			status: http.StatusInternalServerError, code: httpapi.CodeInternal},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			h := newHarness(t, withFinder(func(context.Context, catalog.Query) (product.Product, error) {
				return product.Product{}, tc.err
			}))
			assertProblem(t, h.get(t, productPath), tc.status, tc.code)
		})
	}
}

func TestGetProductHonoursRequestTimeout(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.RequestTimeout = 20 * time.Millisecond
		d.Products = finderFunc(func(ctx context.Context, _ catalog.Query) (product.Product, error) {
			<-ctx.Done()
			return product.Product{}, ctx.Err()
		})
	})
	started := time.Now()
	assertProblem(t, h.get(t, productPath), http.StatusServiceUnavailable,
		httpapi.CodeServiceUnavailable)
	if time.Since(started) > defaultTimeout {
		t.Fatal("request timeout was not enforced")
	}
}

func TestPanicIsRecoveredAsInternalError(t *testing.T) {
	h := newHarness(t, withFinder(func(context.Context, catalog.Query) (product.Product, error) {
		panic("boom")
	}))
	assertProblem(t, h.get(t, productPath), http.StatusInternalServerError, httpapi.CodeInternal)
	if !strings.Contains(h.logs.String(), "panic recovered") {
		t.Fatal("panic must be logged")
	}
	if got := h.recorder.last(); got.status != http.StatusInternalServerError {
		t.Fatalf("want 500 recorded, got %+v", got)
	}
}

func TestUnknownRouteReturnsProblem(t *testing.T) {
	h := newHarness(t)
	assertProblem(t, h.get(t, "/nope"), http.StatusNotFound, httpapi.CodeResourceNotFound)
	rec := h.do(t, mustRequest(t, http.MethodPost, productPath))
	assertProblem(t, rec, http.StatusNotFound, httpapi.CodeResourceNotFound)
}

func withFinder(
	f func(context.Context, catalog.Query) (product.Product, error),
) func(*httpapi.Dependencies) {
	return func(d *httpapi.Dependencies) { d.Products = finderFunc(f) }
}

func mustRequest(t *testing.T, method, target string) *http.Request {
	t.Helper()
	req, err := http.NewRequestWithContext(context.Background(), method, target, nil)
	if err != nil {
		t.Fatalf("new request: %v", err)
	}
	return req
}
