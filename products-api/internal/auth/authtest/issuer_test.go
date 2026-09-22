package authtest_test

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
)

func TestIssuerServesJWKSAndSignsTokens(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	req, err := http.NewRequestWithContext(t.Context(), http.MethodGet, issuer.JWKSURL, nil)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("get jwks: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()
	var set struct {
		Keys []map[string]string `json:"keys"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&set); err != nil || len(set.Keys) != 1 {
		t.Fatalf("want one key, got %+v err=%v", set, err)
	}
	const jwtSegments = 3
	token := issuer.Token(t, authtest.ValidClaims())
	if len(strings.Split(token, ".")) != jwtSegments {
		t.Fatalf("want compact jwt, got %q", token)
	}
}
