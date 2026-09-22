package httpapi_test

import (
	"context"
	"errors"
	"net/http"
	"strings"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
)

type verifierFunc func(ctx context.Context, token string) error

func (f verifierFunc) Verify(ctx context.Context, token string) error { return f(ctx, token) }

func authHarness(t *testing.T) (harness, *authtest.Issuer) {
	t.Helper()
	issuer := authtest.NewIssuer(t)
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	verifier, err := auth.NewJWKSVerifier(ctx, auth.Settings{
		Issuer: authtest.IssuerURL, JWKSURL: issuer.JWKSURL, RequiredRole: authtest.RequiredRole,
	})
	if err != nil {
		t.Fatalf("verifier: %v", err)
	}
	return newHarness(t, func(d *httpapi.Dependencies) { d.Verifier = verifier }), issuer
}

func TestAuthentication(t *testing.T) {
	h, issuer := authHarness(t)
	noRole := authtest.ValidClaims()
	noRole.Roles = nil
	valid := issuer.Token(t, authtest.ValidClaims())
	cases := []struct {
		name   string
		header string
		status int
		code   httpapi.Code
	}{
		{name: "should_reject_missing_header", status: http.StatusUnauthorized,
			code: httpapi.CodeUnauthorized},
		{name: "should_reject_basic_scheme", header: "Basic abc", status: http.StatusUnauthorized,
			code: httpapi.CodeUnauthorized},
		{name: "should_reject_invalid_token", header: "Bearer abc.def.ghi",
			status: http.StatusUnauthorized, code: httpapi.CodeUnauthorized},
		{name: "should_forbid_without_role", header: "Bearer " + issuer.Token(t, noRole),
			status: http.StatusForbidden, code: httpapi.CodeForbidden},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			rec := h.get(t, productPath, "Authorization", tc.header)
			assertProblem(t, rec, tc.status, tc.code)
		})
	}
	if rec := h.get(t, productPath, "Authorization", "bearer "+valid); rec.Code != http.StatusOK {
		t.Fatalf("want 200 with valid token, got %d %s", rec.Code, rec.Body.String())
	}
	if strings.Contains(h.logs.String(), valid) {
		t.Fatal("tokens must never be logged")
	}
}

func TestUnauthorizedIncludesChallenge(t *testing.T) {
	h, _ := authHarness(t)
	if got := h.get(t, productPath).Header().Get("WWW-Authenticate"); got != "Bearer" {
		t.Fatalf("want bearer challenge, got %q", got)
	}
}

func TestAuthenticationUnexpectedErrorIsInternal(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.Verifier = verifierFunc(func(context.Context, string) error { return errors.New("x") })
	})
	assertProblem(t, h.get(t, productPath), http.StatusInternalServerError, httpapi.CodeInternal)
}

func TestHealthAndMetricsSkipAuthentication(t *testing.T) {
	h, _ := authHarness(t)
	for _, target := range []string{"/health/live", "/health/ready", "/metrics"} {
		if rec := h.get(t, target); rec.Code != http.StatusOK {
			t.Fatalf("%s: want 200, got %d", target, rec.Code)
		}
	}
}

func TestRateLimit(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.RateLimit = httpapi.RateLimit{RPS: 0.5, Burst: 1}
	})
	if rec := h.get(t, productPath); rec.Code != http.StatusOK {
		t.Fatalf("first request must pass, got %d", rec.Code)
	}
	rec := h.get(t, productPath)
	assertProblem(t, rec, http.StatusTooManyRequests, httpapi.CodeRateLimited)
	if got := rec.Header().Get("Retry-After"); got != "2" {
		t.Fatalf("want Retry-After 2, got %q", got)
	}
	if rec := h.get(t, "/health/live"); rec.Code != http.StatusOK {
		t.Fatal("health must not be rate limited")
	}
}

func TestRateLimitRetryAfterHasFloorOfOneSecond(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.RateLimit = httpapi.RateLimit{RPS: 2, Burst: 1}
	})
	h.get(t, productPath)
	rec := h.get(t, productPath)
	if rec.Code != http.StatusTooManyRequests || rec.Header().Get("Retry-After") != "1" {
		t.Fatalf("want 429 with Retry-After 1, got %d %q", rec.Code, rec.Header().Get("Retry-After"))
	}
}
