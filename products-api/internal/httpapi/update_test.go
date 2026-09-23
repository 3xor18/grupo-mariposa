package httpapi_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
)

const patchPath = "/products/PRD-020?market=MX"

func roleVerifier(role string) verifierFunc {
	return func(_ context.Context, token, required string) (auth.Principal, error) {
		switch {
		case token == "":
			return auth.Principal{}, auth.ErrUnauthenticated
		case required != role:
			return auth.Principal{}, auth.ErrForbidden
		default:
			return auth.Principal{ID: "tester"}, nil
		}
	}
}

func patchRequest(t *testing.T, target, body string, headers ...string) *http.Request {
	t.Helper()
	req := httptest.NewRequestWithContext(t.Context(), http.MethodPatch, target,
		strings.NewReader(body))
	req.RemoteAddr = testRemoteAddr
	req.Header.Set("Content-Type", "application/json")
	for i := 0; i+1 < len(headers); i += 2 {
		req.Header.Set(headers[i], headers[i+1])
	}
	return req
}

func decodeProduct(t *testing.T, body []byte) map[string]any {
	t.Helper()
	var decoded map[string]any
	if err := json.Unmarshal(body, &decoded); err != nil {
		t.Fatalf("decode: %v", err)
	}
	return decoded
}

func TestPatchUpdatesProductAndReturnsNewVersion(t *testing.T) {
	h := newHarness(t)
	rec := h.do(t, patchRequest(t, patchPath, `{"status":"DISCONTINUED","name":"Demo"}`,
		"If-Match", `"1"`))
	if rec.Code != http.StatusOK || rec.Header().Get("ETag") != `"2"` {
		t.Fatalf("want 200 with ETag 2, got %d %q %s", rec.Code, rec.Header().Get("ETag"),
			rec.Body.String())
	}
	body := decodeProduct(t, rec.Body.Bytes())
	if body["status"] != "DISCONTINUED" || body["name"] != "Demo" || body["version"] != 2.0 {
		t.Fatalf("unexpected body %v", body)
	}
	got := h.get(t, patchPath)
	if got.Header().Get("ETag") != `"2"` || decodeProduct(t, got.Body.Bytes())["name"] != "Demo" {
		t.Fatal("update must be visible to subsequent reads")
	}
}

func TestPatchIfMatchVariants(t *testing.T) {
	cases := map[string]int{
		"":      http.StatusOK,
		"*":     http.StatusOK,
		"1":     http.StatusOK,
		`"1"`:   http.StatusOK,
		`"9"`:   http.StatusPreconditionFailed,
		`W/"1"`: http.StatusBadRequest,
		`"0"`:   http.StatusBadRequest,
		"abc":   http.StatusBadRequest,
	}
	for ifMatch, want := range cases {
		t.Run(ifMatch, func(t *testing.T) {
			h := newHarness(t)
			rec := h.do(t, patchRequest(t, patchPath, `{"taxCategory":"EXEMPT"}`,
				"If-Match", ifMatch))
			if rec.Code != want {
				t.Fatalf("If-Match %q: want %d, got %d %s", ifMatch, want, rec.Code,
					rec.Body.String())
			}
		})
	}
}

func TestPatchStaleVersionReturns412(t *testing.T) {
	h := newHarness(t)
	h.do(t, patchRequest(t, patchPath, `{"name":"First"}`, "If-Match", `"1"`))
	rec := h.do(t, patchRequest(t, patchPath, `{"name":"Second"}`, "If-Match", `"1"`))
	assertProblem(t, rec, http.StatusPreconditionFailed, httpapi.CodePreconditionFailed)
}

func TestPatchValidation(t *testing.T) {
	cases := []struct {
		name   string
		target string
		body   string
		fields []string
	}{
		{name: "should_reject_empty_object", target: patchPath, body: `{}`,
			fields: []string{"body"}},
		{name: "should_reject_malformed_json", target: patchPath, body: `{"name":`,
			fields: []string{"body"}},
		{name: "should_reject_null_body", target: patchPath, body: `null`,
			fields: []string{"body"}},
		{name: "should_reject_array", target: patchPath, body: `[]`, fields: []string{"body"}},
		{name: "should_reject_trailing_data", target: patchPath, body: `{"name":"a"} {}`,
			fields: []string{"body"}},
		{name: "should_reject_huge_body", target: patchPath,
			body: `{"name":"` + strings.Repeat("a", 17<<10) + `"}`, fields: []string{"body"}},
		{name: "should_reject_unknown_fields", target: patchPath,
			body: `{"sku":"X","price":1}`, fields: []string{"price", "sku"}},
		{name: "should_reject_null_and_non_string", target: patchPath,
			body: `{"name":null,"status":1}`, fields: []string{"name", "status"}},
		{name: "should_reject_invalid_values", target: patchPath,
			body:   `{"name":" ","status":"GONE","taxCategory":"ZERO"}`,
			fields: []string{"name", "status", "taxCategory"}},
		{name: "should_reject_bad_path_and_market", target: "/products/bad?market=US",
			body: `{"name":"a"}`, fields: []string{"productId", "market"}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := newHarness(t).do(t, patchRequest(t, tc.target, tc.body))
			problem := assertProblem(t, rec, http.StatusBadRequest, httpapi.CodeValidation)
			if len(problem.Errors) != len(tc.fields) {
				t.Fatalf("want fields %v, got %+v", tc.fields, problem.Errors)
			}
			for i, field := range tc.fields {
				if problem.Errors[i].Field != field {
					t.Fatalf("error %d: want %s, got %+v", i, field, problem.Errors[i])
				}
			}
		})
	}
}

func TestPatchReportsIfMatchWithBodyErrors(t *testing.T) {
	rec := newHarness(t).do(t, patchRequest(t, patchPath, `{`, "If-Match", "x"))
	problem := assertProblem(t, rec, http.StatusBadRequest, httpapi.CodeValidation)
	if len(problem.Errors) != 2 || problem.Errors[0].Field != "If-Match" {
		t.Fatalf("want If-Match and body errors, got %+v", problem.Errors)
	}
}

func TestPatchNotFound(t *testing.T) {
	h := newHarness(t)
	for _, target := range []string{"/products/PRD-999?market=MX", "/products/PRD-002?market=CL"} {
		assertProblem(t, h.do(t, patchRequest(t, target, `{"name":"x"}`)), http.StatusNotFound,
			httpapi.CodeProductNotFound)
	}
}

func TestPatchRequiresAdminRole(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) { d.Verifier = roleVerifier(adminRole) })
	body := `{"name":"x"}`
	assertProblem(t, h.do(t, patchRequest(t, patchPath, body)), http.StatusUnauthorized,
		httpapi.CodeUnauthorized)
	if rec := h.do(t, patchRequest(t, patchPath, body, "Authorization", "Bearer t")); rec.Code !=
		http.StatusOK {
		t.Fatalf("admin must be able to patch, got %d", rec.Code)
	}
	readerOnly := newHarness(t, func(d *httpapi.Dependencies) {
		d.Verifier = roleVerifier(readerRole)
	})
	assertProblem(t, readerOnly.do(t, patchRequest(t, patchPath, body, "Authorization",
		"Bearer t")), http.StatusForbidden, httpapi.CodeForbidden)
	if rec := readerOnly.get(t, patchPath, "Authorization", "Bearer t"); rec.Code != http.StatusOK {
		t.Fatalf("reader must still be able to read, got %d", rec.Code)
	}
}

func TestPatchIsNotAffectedByFaultInjection(t *testing.T) {
	h := newHarness(t, withFaults(t, "PRD-020:503", 0))
	if rec := h.do(t, patchRequest(t, patchPath, `{"name":"x"}`)); rec.Code != http.StatusOK {
		t.Fatalf("faults apply to reads only, got %d", rec.Code)
	}
	assertProblem(t, h.get(t, patchPath), http.StatusServiceUnavailable,
		httpapi.CodeServiceUnavailable)
}
