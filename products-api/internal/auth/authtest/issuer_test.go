package authtest_test

import (
	"encoding/json"
	"net/http"
	"strings"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
)

func fetchStatus(t *testing.T, url string) (int, map[string][]map[string]string) {
	t.Helper()
	req, err := http.NewRequestWithContext(t.Context(), http.MethodGet, url, nil)
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("get jwks: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()
	var set map[string][]map[string]string
	_ = json.NewDecoder(resp.Body).Decode(&set)
	return resp.StatusCode, set
}

func TestIssuerServesJWKSAndSignsTokens(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	status, set := fetchStatus(t, issuer.JWKSURL)
	if status != http.StatusOK || len(set["keys"]) != 1 || set["keys"][0]["kid"] != authtest.KeyID {
		t.Fatalf("unexpected jwks %d %v", status, set)
	}
	issuer.SetAvailable(false)
	if status, _ := fetchStatus(t, issuer.JWKSURL); status != http.StatusServiceUnavailable {
		t.Fatalf("want 503 when unavailable, got %d", status)
	}
	if issuer.Requests() != 2 {
		t.Fatalf("want 2 requests counted, got %d", issuer.Requests())
	}
	const jwtSegments = 3
	claims := authtest.ValidClaims()
	claims.NotBefore, claims.KeyID = time.Now(), ""
	if token := issuer.Token(t, claims); len(strings.Split(token, ".")) != jwtSegments {
		t.Fatalf("want compact jwt, got %q", token)
	}
}
