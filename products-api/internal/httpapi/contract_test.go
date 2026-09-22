package httpapi_test

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/getkin/kin-openapi/openapi3"
	"github.com/getkin/kin-openapi/openapi3filter"
	"github.com/getkin/kin-openapi/routers"
	"github.com/getkin/kin-openapi/routers/gorillamux"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
	"github.com/grupomariposa/platform/products-api/internal/ratelimit"
)

const (
	openAPIPath        = "../../../contracts/http/products-api.openapi.yaml"
	contractHost       = "http://localhost:8081"
	problemResponse    = "Problem"
	problemContentType = "application/problem+json"
)

type contract struct {
	router  routers.Router
	problem *openapi3.Schema
}

func loadContract(t *testing.T) contract {
	t.Helper()
	loader := openapi3.NewLoader()
	loader.IsExternalRefsAllowed = true
	doc, err := loader.LoadFromFile(openAPIPath)
	if err != nil {
		t.Fatalf("load openapi: %v", err)
	}
	if err := doc.Validate(context.Background()); err != nil {
		t.Fatalf("invalid openapi: %v", err)
	}
	router, err := gorillamux.NewRouter(doc)
	if err != nil {
		t.Fatalf("router: %v", err)
	}
	schema := doc.Components.Responses[problemResponse].Value.Content.Get(problemContentType).Schema
	return contract{router: router, problem: schema.Value}
}

func (c contract) validate(t *testing.T, handler http.Handler, req *http.Request) int {
	t.Helper()
	rec := httptest.NewRecorder()
	handler.ServeHTTP(rec, req)
	c.validateAgainstOpenAPI(t, req, rec)
	if rec.Header().Get("Content-Type") == problemContentType {
		c.validateProblem(t, rec.Body.Bytes())
	}
	return rec.Code
}

func (c contract) validateAgainstOpenAPI(
	t *testing.T, req *http.Request, rec *httptest.ResponseRecorder,
) {
	t.Helper()
	route, params, err := c.router.FindRoute(req)
	if err != nil {
		t.Fatalf("find route %s: %v", req.URL, err)
	}
	input := &openapi3filter.ResponseValidationInput{
		RequestValidationInput: &openapi3filter.RequestValidationInput{
			Request: req, PathParams: params, Route: route,
		},
		Status:  rec.Code,
		Header:  rec.Header(),
		Body:    io.NopCloser(bytes.NewReader(rec.Body.Bytes())),
		Options: &openapi3filter.Options{IncludeResponseStatus: true},
	}
	if err := openapi3filter.ValidateResponse(req.Context(), input); err != nil {
		t.Fatalf("%s %d violates contract: %v\n%s", req.URL, rec.Code, err, rec.Body.String())
	}
}

func (c contract) validateProblem(t *testing.T, body []byte) {
	t.Helper()
	var value any
	if err := json.Unmarshal(body, &value); err != nil {
		t.Fatalf("decode problem: %v", err)
	}
	if err := c.problem.VisitJSON(value, openapi3.EnableFormatValidation()); err != nil {
		t.Fatalf("problem violates schema: %v\n%s", err, body)
	}
}

func contractRequest(t *testing.T, target string) *http.Request {
	t.Helper()
	return newRequest(t, http.MethodGet, contractHost+target)
}

func TestContractProductResponses(t *testing.T) {
	c := loadContract(t)
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.ClientLimiter = ratelimit.NewKeyed(1e-3, 1, limiterCapacity)
	})
	for _, want := range []int{http.StatusOK, http.StatusTooManyRequests} {
		if got := c.validate(t, h.handler, contractRequest(t, productPath)); got != want {
			t.Fatalf("want %d, got %d", want, got)
		}
	}
}

func TestContractProblemResponses(t *testing.T) {
	c := loadContract(t)
	h := newHarness(t, withFaults(t, "PRD-003:500,PRD-008:503", time.Second))
	cases := map[string]int{
		"/products/PRD-999?market=MX": http.StatusNotFound,
		"/products/PRD-002?market=PE": http.StatusNotFound,
		"/products/PRD-001?market=US": http.StatusBadRequest,
		"/products/PRD-001":           http.StatusBadRequest,
		"/products/PRD-003?market=MX": http.StatusInternalServerError,
		"/products/PRD-008?market=MX": http.StatusServiceUnavailable,
	}
	for target, want := range cases {
		if got := c.validate(t, h.handler, contractRequest(t, target)); got != want {
			t.Fatalf("%s: want %d, got %d", target, want, got)
		}
	}
}

func TestContractAuthProblems(t *testing.T) {
	c := loadContract(t)
	h, _ := authHarness(t)
	if got := c.validate(t, h.handler, contractRequest(t, productPath)); got != 401 {
		t.Fatalf("want 401, got %d", got)
	}
	cases := map[error]int{auth.ErrForbidden: 403, auth.ErrUnavailable: 503}
	for err, want := range cases {
		failing := newHarness(t, func(d *httpapi.Dependencies) { d.Verifier = failingVerifier(err) })
		if got := c.validate(t, failing.handler, contractRequest(t, productPath)); got != want {
			t.Fatalf("want %d, got %d", want, got)
		}
	}
}

func TestContractHealthResponses(t *testing.T) {
	c := loadContract(t)
	cases := map[bool]int{true: http.StatusOK, false: http.StatusServiceUnavailable}
	for ready, want := range cases {
		h := newHarness(t, func(d *httpapi.Dependencies) { d.Readiness = readiness{ready} })
		if got := c.validate(t, h.handler, contractRequest(t, "/health/live")); got != 200 {
			t.Fatalf("live: want 200, got %d", got)
		}
		if got := c.validate(t, h.handler, contractRequest(t, "/health/ready")); got != want {
			t.Fatalf("ready=%v: want %d, got %d", ready, want, got)
		}
	}
}

func TestContractProblemsOutsideOpenAPIStatuses(t *testing.T) {
	c := loadContract(t)
	h := newHarness(t, withFaults(t, "PRD-004:502", time.Second))
	cases := []struct {
		method, target string
		status         int
	}{
		{http.MethodGet, "/products/PRD-004?market=MX", http.StatusBadGateway},
		{http.MethodGet, "/unknown", http.StatusNotFound},
		{http.MethodPost, productPath, http.StatusMethodNotAllowed},
	}
	for _, tc := range cases {
		rec := h.do(t, newRequest(t, tc.method, tc.target))
		if rec.Code != tc.status {
			t.Fatalf("%s %s: want %d, got %d", tc.method, tc.target, tc.status, rec.Code)
		}
		c.validateProblem(t, rec.Body.Bytes())
	}
}

func TestContractDetectsViolations(t *testing.T) {
	c := loadContract(t)
	incomplete := map[string]any{"type": "about:blank", "title": "x", "status": 400}
	if err := c.problem.VisitJSON(incomplete); err == nil {
		t.Fatal("problem schema must reject missing required fields")
	}
	bogus := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write([]byte(`{"productId":"PRD-001","status":"UNKNOWN"}`))
	})
	req := contractRequest(t, productPath)
	rec := httptest.NewRecorder()
	bogus.ServeHTTP(rec, req)
	route, params, _ := c.router.FindRoute(req)
	input := &openapi3filter.ResponseValidationInput{
		RequestValidationInput: &openapi3filter.RequestValidationInput{
			Request: req, PathParams: params, Route: route,
		},
		Status: rec.Code, Header: rec.Header(),
		Body: io.NopCloser(bytes.NewReader(rec.Body.Bytes())),
	}
	if openapi3filter.ValidateResponse(req.Context(), input) == nil {
		t.Fatal("openapi validation must reject an invalid product body")
	}
}
