package httpapi_test

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
	"github.com/grupomariposa/platform/products-api/internal/ratelimit"
)

type verifierFunc func(ctx context.Context, token string) (auth.Principal, error)

func (f verifierFunc) Verify(ctx context.Context, token string) (auth.Principal, error) {
	return f(ctx, token)
}

func failingVerifier(err error) verifierFunc {
	return func(context.Context, string) (auth.Principal, error) { return auth.Principal{}, err }
}

func authHarness(t *testing.T, customize ...func(*httpapi.Dependencies)) (harness,
	*authtest.Issuer,
) {
	t.Helper()
	issuer := authtest.NewIssuer(t)
	verifier := auth.NewVerifier(auth.Settings{
		Issuer: authtest.IssuerURL, Audience: authtest.Audience, JWKSURL: issuer.JWKSURL,
		RequiredRole: authtest.RequiredRole, JWKSTimeout: time.Second, Refresh: time.Hour,
		MinimumRefresh: time.Millisecond,
	}, slog.New(slog.DiscardHandler))
	options := append([]func(*httpapi.Dependencies){
		func(d *httpapi.Dependencies) { d.Verifier = verifier },
	}, customize...)
	return newHarness(t, options...), issuer
}

func bearer(token string) []string {
	return []string{"Authorization", "Bearer " + token}
}

func TestAuthentication(t *testing.T) {
	h, issuer := authHarness(t)
	noRole := authtest.ValidClaims()
	noRole.Roles = nil
	wrongAudience := authtest.ValidClaims()
	wrongAudience.Audience = []string{"account"}
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
		{name: "should_reject_wrong_audience", header: "Bearer " + issuer.Token(t, wrongAudience),
			status: http.StatusUnauthorized, code: httpapi.CodeUnauthorized},
		{name: "should_forbid_without_role", header: "Bearer " + issuer.Token(t, noRole),
			status: http.StatusForbidden, code: httpapi.CodeForbidden},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			assertProblem(t, h.get(t, productPath, "Authorization", tc.header), tc.status, tc.code)
		})
	}
	valid := issuer.Token(t, authtest.ValidClaims())
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

func TestAuthenticationBackendFailures(t *testing.T) {
	cases := []struct {
		name   string
		err    error
		status int
		code   httpapi.Code
	}{
		{name: "should_map_unavailable_to_503", err: auth.ErrUnavailable,
			status: http.StatusServiceUnavailable, code: httpapi.CodeServiceUnavailable},
		{name: "should_map_unexpected_to_500", err: errors.New("x"),
			status: http.StatusInternalServerError, code: httpapi.CodeInternal},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			h := newHarness(t, func(d *httpapi.Dependencies) {
				d.Verifier = failingVerifier(tc.err)
			})
			assertProblem(t, h.get(t, productPath), tc.status, tc.code)
		})
	}
}

func TestJWKSDownReturns503(t *testing.T) {
	h, issuer := authHarness(t)
	token := issuer.Token(t, authtest.ValidClaims())
	issuer.SetAvailable(false)
	assertProblem(t, h.get(t, productPath, bearer(token)...), http.StatusServiceUnavailable,
		httpapi.CodeServiceUnavailable)
}

func TestHealthAndMetricsSkipAuthentication(t *testing.T) {
	h, _ := authHarness(t)
	for _, target := range []string{"/health/live", "/health/ready", "/metrics"} {
		if rec := h.get(t, target); rec.Code != http.StatusOK {
			t.Fatalf("%s: want 200, got %d", target, rec.Code)
		}
	}
}

func TestRateLimitPerClientAddress(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.ClientLimiter = ratelimit.NewKeyed(0.5, 1, limiterCapacity)
	})
	if rec := h.get(t, productPath); rec.Code != http.StatusOK {
		t.Fatalf("first request must pass, got %d", rec.Code)
	}
	rec := assertLimited(t, h.get(t, productPath))
	if got := rec.Header().Get("Retry-After"); got != "2" {
		t.Fatalf("want Retry-After derived from delay (2s), got %q", got)
	}
	other := newRequest(t, http.MethodGet, productPath)
	other.RemoteAddr = "198.51.100.7:9999"
	if rec := h.do(t, other); rec.Code != http.StatusOK {
		t.Fatalf("other client must have its own bucket, got %d", rec.Code)
	}
	if rec := h.get(t, "/health/live"); rec.Code != http.StatusOK {
		t.Fatal("health must not be rate limited")
	}
}

func TestRateLimitFallsBackToRawRemoteAddress(t *testing.T) {
	h := newHarness(t, func(d *httpapi.Dependencies) {
		d.ClientLimiter = ratelimit.NewKeyed(2, 1, limiterCapacity)
	})
	first := newRequest(t, http.MethodGet, productPath)
	first.RemoteAddr = "unix-socket"
	h.do(t, first)
	second := newRequest(t, http.MethodGet, productPath)
	second.RemoteAddr = "unix-socket"
	if rec := assertLimited(t, h.do(t, second)); rec.Header().Get("Retry-After") != "1" {
		t.Fatalf("want Retry-After floor of 1, got %q", rec.Header().Get("Retry-After"))
	}
}

func TestRateLimitPerPrincipal(t *testing.T) {
	h, issuer := authHarness(t, func(d *httpapi.Dependencies) {
		d.PrincipalLimiter = ratelimit.NewKeyed(0.5, 1, limiterCapacity)
	})
	first := issuer.Token(t, authtest.ValidClaims())
	otherClaims := authtest.ValidClaims()
	otherClaims.AuthorizedParty = "billing-service"
	other := issuer.Token(t, otherClaims)
	if rec := h.get(t, productPath, bearer(first)...); rec.Code != http.StatusOK {
		t.Fatalf("first call must pass, got %d", rec.Code)
	}
	assertLimited(t, h.get(t, productPath, bearer(first)...))
	if rec := h.get(t, productPath, bearer(other)...); rec.Code != http.StatusOK {
		t.Fatalf("other principal behind the same address must pass, got %d", rec.Code)
	}
}

func assertLimited(t *testing.T, rec *httptest.ResponseRecorder) *httptest.ResponseRecorder {
	t.Helper()
	assertProblem(t, rec, http.StatusTooManyRequests, httpapi.CodeRateLimited)
	return rec
}
