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
	rec := newHarness(t).get(t, "/products/PRD-001?market=PE")
	if rec.Code != http.StatusOK || rec.Header().Get("Content-Type") != "application/json" {
		t.Fatalf("unexpected response %d %s", rec.Code, rec.Header().Get("Content-Type"))
	}
	var body map[string]any
	if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode: %v", err)
	}
	want := map[string]any{
		"productId": "PRD-001", "name": "Bebida 600 ml", "sku": "BEB-600-PET",
		"status": "ACTIVE", "taxCategory": "STANDARD", "version": float64(1),
	}
	if len(body) != len(want) {
		t.Fatalf("want exactly %d fields, got %v", len(want), body)
	}
	for key, value := range want {
		if body[key] != value {
			t.Fatalf("field %s: want %q, got %q", key, value, body[key])
		}
	}
	if rec.Header().Get("ETag") != `"1"` {
		t.Fatalf("want ETag \"1\", got %q", rec.Header().Get("ETag"))
	}
	if rec.Header().Get("Cache-Control") != "" {
		t.Fatal("successful responses must not be marked no-store")
	}
}

func TestHeadProductHasNoBody(t *testing.T) {
	h := newHarness(t)
	rec := h.do(t, newRequest(t, http.MethodHead, productPath))
	if rec.Code != http.StatusOK {
		t.Fatalf("want 200 for HEAD, got %d", rec.Code)
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
		{name: "should_accept_catalog_markets_only", target: "/products/PRD-001?market=BR",
			fields: []string{"market"}},
		{name: "should_report_every_field", target: "/products/prd-1?market=mx",
			fields: []string{"productId", "market"}},
	}
	h := newHarness(t)
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			problem := assertProblem(t, h.get(t, tc.target), http.StatusBadRequest,
				httpapi.CodeValidation)
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
	cases := map[string]string{
		"/products/PRD-999?market=MX": "/products/PRD-999",
		"/products/PRD-002?market=CO": "/products/PRD-002",
	}
	for target, instance := range cases {
		problem := assertProblem(t, h.get(t, target), http.StatusNotFound,
			httpapi.CodeProductNotFound)
		if problem.Instance != instance || len(problem.Errors) != 0 {
			t.Fatalf("unexpected problem %+v", problem)
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
		{name: "should_map_deadline_to_503", err: context.DeadlineExceeded,
			status: http.StatusServiceUnavailable, code: httpapi.CodeServiceUnavailable},
		{name: "should_map_client_cancel_to_499", err: context.Canceled,
			status: httpapi.StatusClientClosed, code: httpapi.CodeClientClosed},
		{name: "should_map_unexpected_to_500", err: errors.New("disk on fire"),
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
	const timeout = 20 * time.Millisecond
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.RequestTimeout = timeout
		d.Products = finderFunc(func(ctx context.Context, _ catalog.Query) (product.Product, error) {
			<-ctx.Done()
			return product.Product{}, ctx.Err()
		})
	})
	started := time.Now()
	assertProblem(t, h.get(t, productPath), http.StatusServiceUnavailable,
		httpapi.CodeServiceUnavailable)
	if elapsed := time.Since(started); elapsed < timeout || elapsed > defaultTimeout {
		t.Fatalf("request timeout not enforced, took %v", elapsed)
	}
}

func TestPanicIsRecoveredAsInternalError(t *testing.T) {
	h := newHarness(t, withFinder(func(context.Context, catalog.Query) (product.Product, error) {
		panic("boom")
	}))
	assertProblem(t, h.get(t, productPath), http.StatusInternalServerError, httpapi.CodeInternal)
	if !strings.Contains(h.logs.String(), `"message":"panic recovered"`) {
		t.Fatal("panic must be logged")
	}
	if got := h.recorder.last(); got.status != http.StatusInternalServerError {
		t.Fatalf("want 500 recorded, got %+v", got)
	}
}

func TestPanicAfterHeaderDoesNotRewriteResponse(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.MetricsHandler = http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
			w.WriteHeader(http.StatusAccepted)
			panic("late")
		})
	})
	rec := h.get(t, "/metrics")
	if rec.Code != http.StatusAccepted || rec.Body.Len() != 0 {
		t.Fatalf("response must not be rewritten, got %d %q", rec.Code, rec.Body.String())
	}
	if !strings.Contains(h.logs.String(), "panic recovered after response started") {
		t.Fatal("late panic must be logged")
	}
}

func TestUnknownPathReturnsStaticNotFound(t *testing.T) {
	h := newHarness(t)
	for _, target := range []string{"/nope", "/products/", "/products/PRD-001/x",
		"/%3Cscript%3E"} {
		problem := assertProblem(t, h.get(t, target), http.StatusNotFound,
			httpapi.CodeResourceNotFound)
		if strings.Contains(problem.Detail, "/") || strings.Contains(problem.Detail, "script") {
			t.Fatalf("detail must not reflect input: %q", problem.Detail)
		}
	}
}

func TestWrongMethodOnKnownRouteReturns405(t *testing.T) {
	h := newHarness(t)
	allow := map[string]string{productPath: "GET, HEAD, PATCH", "/health/live": "GET, HEAD",
		"/health/ready": "GET, HEAD", "/metrics": "GET, HEAD"}
	for target, want := range allow {
		for _, method := range []string{http.MethodPost, http.MethodDelete, "BREW"} {
			rec := h.do(t, newRequest(t, method, target))
			assertProblem(t, rec, http.StatusMethodNotAllowed, httpapi.CodeMethodNotAllowed)
			if rec.Header().Get("Allow") != want {
				t.Fatalf("%s %s: unexpected Allow %q", method, target, rec.Header().Get("Allow"))
			}
		}
	}
}

func withFinder(
	f func(context.Context, catalog.Query) (product.Product, error),
) func(*httpapi.Dependencies) {
	return func(d *httpapi.Dependencies) { d.Products = finderFunc(f) }
}
