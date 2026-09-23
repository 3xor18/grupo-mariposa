package auth_test

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
)

const (
	fastRefresh = 10 * time.Millisecond
	waitLimit   = 5 * time.Second
)

func settings(issuer *authtest.Issuer) auth.Settings {
	return auth.Settings{
		Issuer:         authtest.IssuerURL,
		Audience:       authtest.Audience,
		JWKSURL:        issuer.JWKSURL,
		RequiredRole:   authtest.RequiredRole,
		JWKSTimeout:    time.Second,
		Refresh:        time.Hour,
		MinimumRefresh: fastRefresh,
	}
}

func newVerifier(s auth.Settings) *auth.Verifier {
	return auth.NewVerifier(s, slog.New(slog.DiscardHandler))
}

func TestVerifyAcceptsValidToken(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	principal, err := newVerifier(settings(issuer)).Verify(context.Background(),
		issuer.Token(t, authtest.ValidClaims()))
	if err != nil || principal.ID != authtest.Client {
		t.Fatalf("want principal %q, got %+v err=%v", authtest.Client, principal, err)
	}
}

func TestVerifyFallsBackToSubject(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	claims := authtest.ValidClaims()
	claims.AuthorizedParty = ""
	principal, err := newVerifier(settings(issuer)).Verify(context.Background(),
		issuer.Token(t, claims))
	if err != nil || principal.ID != claims.Subject {
		t.Fatalf("want subject principal, got %+v err=%v", principal, err)
	}
}

func TestVerifyRejectsInvalidTokens(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	verifier := newVerifier(settings(issuer))
	cases := []struct {
		name  string
		token string
		want  error
	}{
		{name: "should_reject_missing_token", want: auth.ErrUnauthenticated},
		{name: "should_reject_garbage", token: "not-a-jwt", want: auth.ErrUnauthenticated},
		{name: "should_reject_expired", want: auth.ErrUnauthenticated, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.ExpiresAt = time.Now().Add(-time.Hour) }))},
		{name: "should_reject_missing_exp", want: auth.ErrUnauthenticated, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.ExpiresAt = time.Time{} }))},
		{name: "should_reject_future_nbf", want: auth.ErrUnauthenticated, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.NotBefore = time.Now().Add(time.Hour) }))},
		{name: "should_reject_wrong_issuer", want: auth.ErrUnauthenticated, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.Issuer = "http://evil.test" }))},
		{name: "should_reject_wrong_audience", want: auth.ErrUnauthenticated, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.Audience = []string{"account"} }))},
		{name: "should_reject_unknown_kid", want: auth.ErrUnauthenticated, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.KeyID = "rotated-away" }))},
		{name: "should_reject_missing_kid", want: auth.ErrUnauthenticated, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.KeyID = "" }))},
		{name: "should_reject_missing_subject", want: auth.ErrUnauthenticated,
			token: issuer.Token(t, with(func(c *authtest.Claims) {
				c.Subject, c.AuthorizedParty = "", ""
			}))},
		{name: "should_reject_foreign_signature", want: auth.ErrUnauthenticated,
			token: authtest.SignWith(t, authtest.NewKey(t), authtest.ValidClaims())},
		{name: "should_reject_alg_none", token: noneToken(t), want: auth.ErrUnauthenticated},
		{name: "should_reject_hmac", token: hmacToken(t), want: auth.ErrUnauthenticated},
		{name: "should_forbid_without_role", want: auth.ErrForbidden, token: issuer.Token(t,
			with(func(c *authtest.Claims) { c.Roles = []string{"other-role"} }))},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := verifier.Verify(context.Background(), tc.token); !errors.Is(err, tc.want) {
				t.Fatalf("want %v, got %v", tc.want, err)
			}
		})
	}
}

func TestVerifyAlwaysChecksAudience(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	s := settings(issuer)
	s.Audience = ""
	token := issuer.Token(t, with(func(c *authtest.Claims) { c.Audience = []string{"account"} }))
	if _, err := newVerifier(s).Verify(context.Background(), token); !errors.Is(err,
		auth.ErrUnauthenticated) {
		t.Fatalf("audience must never fail open, got %v", err)
	}
}

func TestVerifyReportsUnavailableJWKS(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	issuer.SetAvailable(false)
	verifier := newVerifier(settings(issuer))
	token := issuer.Token(t, authtest.ValidClaims())
	for range 2 {
		if _, err := verifier.Verify(context.Background(), token); !errors.Is(err,
			auth.ErrUnavailable) {
			t.Fatalf("want ErrUnavailable while jwks is down, got %v", err)
		}
	}
	if verifier.Ready() {
		t.Fatal("verifier must not be ready before keys are loaded")
	}
}

func TestVerifyReportsCancelledContextAsUnavailable(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	_, err := newVerifier(settings(issuer)).Verify(ctx, issuer.Token(t, authtest.ValidClaims()))
	if !errors.Is(err, auth.ErrUnavailable) {
		t.Fatalf("want ErrUnavailable for cancelled context, got %v", err)
	}
}

func TestRunWarmsUpAndRetries(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	issuer.SetAvailable(false)
	verifier := newVerifier(settings(issuer))
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() {
		verifier.Run(ctx)
		close(done)
	}()
	t.Cleanup(func() {
		cancel()
		<-done
	})
	waitFor(t, func() bool { return issuer.Requests() >= 2 })
	if verifier.Ready() {
		t.Fatal("must not be ready while jwks is down")
	}
	issuer.SetAvailable(true)
	waitFor(t, verifier.Ready)
	if _, err := verifier.Verify(ctx, issuer.Token(t, authtest.ValidClaims())); err != nil {
		t.Fatalf("want valid token after warm-up, got %v", err)
	}
}

func waitFor(t *testing.T, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(waitLimit)
	for !condition() {
		if time.Now().After(deadline) {
			t.Fatal("condition not met in time")
		}
		time.Sleep(fastRefresh)
	}
}

func with(mutate func(*authtest.Claims)) authtest.Claims {
	claims := authtest.ValidClaims()
	mutate(&claims)
	return claims
}

func hmacToken(t *testing.T) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"iss": authtest.IssuerURL, "exp": time.Now().Add(time.Hour).Unix(),
	})
	token.Header["kid"] = authtest.KeyID
	signed, err := token.SignedString([]byte("shared-secret-for-test"))
	if err != nil {
		t.Fatalf("sign: %v", err)
	}
	return signed
}

func noneToken(t *testing.T) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodNone, jwt.MapClaims{
		"iss": authtest.IssuerURL, "exp": time.Now().Add(time.Hour).Unix(),
	})
	signed, err := token.SignedString(jwt.UnsafeAllowNoneSignatureType)
	if err != nil {
		t.Fatalf("sign: %v", err)
	}
	return signed
}

func TestReadinessStaysDownWithEmptyKeySet(t *testing.T) {
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		requests.Add(1)
		_, _ = io.WriteString(w, `{"keys":[]}`)
	}))
	t.Cleanup(server.Close)
	s := settings(authtest.NewIssuer(t))
	s.JWKSURL = server.URL
	verifier := newVerifier(s)
	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	go func() {
		verifier.Run(ctx)
		close(done)
	}()
	t.Cleanup(func() {
		cancel()
		<-done
	})
	waitFor(t, func() bool { return requests.Load() >= 2 })
	if verifier.Ready() {
		t.Fatal("an empty key set must not make the verifier ready")
	}
}
