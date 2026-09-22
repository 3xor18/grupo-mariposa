package authtest

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	IssuerURL    = "http://issuer.test/realms/mariposa"
	RequiredRole = "products-reader"
	keyID        = "test-key"
	keyBits      = 2048
	tokenTTL     = time.Hour
)

type Issuer struct {
	JWKSURL string
	key     *rsa.PrivateKey
}

type Claims struct {
	Issuer    string
	Roles     []string
	ExpiresAt time.Time
}

func NewIssuer(t testing.TB) *Issuer {
	t.Helper()
	key := NewKey(t)
	server := httptest.NewServer(jwksHandler(&key.PublicKey))
	t.Cleanup(server.Close)
	return &Issuer{JWKSURL: server.URL, key: key}
}

func NewKey(t testing.TB) *rsa.PrivateKey {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, keyBits)
	if err != nil {
		t.Fatalf("generate rsa key: %v", err)
	}
	return key
}

func ValidClaims() Claims {
	return Claims{
		Issuer:    IssuerURL,
		Roles:     []string{RequiredRole},
		ExpiresAt: time.Now().Add(tokenTTL),
	}
}

func (i *Issuer) Token(t testing.TB, claims Claims) string {
	t.Helper()
	return SignWith(t, i.key, claims)
}

func SignWith(t testing.TB, key *rsa.PrivateKey, claims Claims) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"iss":          claims.Issuer,
		"exp":          claims.ExpiresAt.Unix(),
		"sub":          "tester",
		"realm_access": map[string][]string{"roles": claims.Roles},
	})
	token.Header["kid"] = keyID
	signed, err := token.SignedString(key)
	if err != nil {
		t.Fatalf("sign token: %v", err)
	}
	return signed
}

func jwksHandler(public *rsa.PublicKey) http.Handler {
	encode := base64.RawURLEncoding.EncodeToString
	body, _ := json.Marshal(map[string][]map[string]string{"keys": {{
		"kty": "RSA",
		"kid": keyID,
		"use": "sig",
		"alg": "RS256",
		"n":   encode(public.N.Bytes()),
		"e":   encode(big.NewInt(int64(public.E)).Bytes()),
	}}})
	return http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(body)
	})
}
