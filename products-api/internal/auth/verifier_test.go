package auth_test

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
)

func newVerifier(t *testing.T, issuer *authtest.Issuer) *auth.Verifier {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	verifier, err := auth.NewJWKSVerifier(ctx, auth.Settings{
		Issuer:       authtest.IssuerURL,
		JWKSURL:      issuer.JWKSURL,
		RequiredRole: authtest.RequiredRole,
	})
	if err != nil {
		t.Fatalf("create verifier: %v", err)
	}
	return verifier
}

func TestVerify(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	verifier := newVerifier(t, issuer)
	expired := authtest.ValidClaims()
	expired.ExpiresAt = time.Now().Add(-time.Hour)
	wrongIssuer := authtest.ValidClaims()
	wrongIssuer.Issuer = "http://evil.test"
	noRole := authtest.ValidClaims()
	noRole.Roles = []string{"other-role"}
	cases := []struct {
		name  string
		token string
		want  error
	}{
		{name: "should_accept_valid_token", token: issuer.Token(t, authtest.ValidClaims())},
		{name: "should_reject_missing_token", want: auth.ErrUnauthenticated},
		{name: "should_reject_garbage", token: "not-a-jwt", want: auth.ErrUnauthenticated},
		{name: "should_reject_expired", token: issuer.Token(t, expired),
			want: auth.ErrUnauthenticated},
		{name: "should_reject_wrong_issuer", token: issuer.Token(t, wrongIssuer),
			want: auth.ErrUnauthenticated},
		{name: "should_reject_foreign_signature",
			token: authtest.SignWith(t, authtest.NewKey(t), authtest.ValidClaims()),
			want:  auth.ErrUnauthenticated},
		{name: "should_reject_hmac_algorithm", token: hmacToken(t),
			want: auth.ErrUnauthenticated},
		{name: "should_forbid_without_role", token: issuer.Token(t, noRole),
			want: auth.ErrForbidden},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := verifier.Verify(context.Background(), tc.token)
			if !errors.Is(err, tc.want) || (tc.want == nil && err != nil) {
				t.Fatalf("want %v, got %v", tc.want, err)
			}
		})
	}
}

func TestNewJWKSVerifierRejectsInvalidURL(t *testing.T) {
	_, err := auth.NewJWKSVerifier(context.Background(), auth.Settings{JWKSURL: "://bad"})
	if err == nil {
		t.Fatal("want error for invalid jwks url")
	}
}

func hmacToken(t *testing.T) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"iss": authtest.IssuerURL,
		"exp": time.Now().Add(time.Hour).Unix(),
	})
	signed, err := token.SignedString([]byte("shared-secret-for-test"))
	if err != nil {
		t.Fatalf("sign: %v", err)
	}
	return signed
}
