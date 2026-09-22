package authtest

import (
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const (
	IssuerURL    = "http://issuer.test/realms/mariposa"
	RequiredRole = "products-reader"
	Audience     = "products-api"
	Client       = "order-processor"
	KeyID        = "test-key"
	keyBits      = 2048
	tokenTTL     = time.Hour
)

type Issuer struct {
	JWKSURL   string
	key       *rsa.PrivateKey
	available atomic.Bool
	requests  atomic.Int32
}

type Claims struct {
	Issuer          string
	Audience        []string
	Subject         string
	AuthorizedParty string
	Roles           []string
	ExpiresAt       time.Time
	NotBefore       time.Time
	KeyID           string
}

func NewIssuer(t testing.TB) *Issuer {
	t.Helper()
	key := NewKey(t)
	body := jwksBody(t, &key.PublicKey)
	issuer := &Issuer{key: key}
	issuer.available.Store(true)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		issuer.requests.Add(1)
		if !issuer.available.Load() {
			w.WriteHeader(http.StatusServiceUnavailable)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		_, _ = w.Write(body)
	}))
	t.Cleanup(server.Close)
	issuer.JWKSURL = server.URL
	return issuer
}

func (i *Issuer) SetAvailable(available bool) {
	i.available.Store(available)
}

func (i *Issuer) Requests() int {
	return int(i.requests.Load())
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
		Issuer:          IssuerURL,
		Audience:        []string{Audience},
		Subject:         "service-account",
		AuthorizedParty: Client,
		Roles:           []string{RequiredRole},
		ExpiresAt:       time.Now().Add(tokenTTL),
		KeyID:           KeyID,
	}
}

func (i *Issuer) Token(t testing.TB, claims Claims) string {
	t.Helper()
	return SignWith(t, i.key, claims)
}

func SignWith(t testing.TB, key *rsa.PrivateKey, claims Claims) string {
	t.Helper()
	token := jwt.NewWithClaims(jwt.SigningMethodRS256, toMap(claims))
	if claims.KeyID != "" {
		token.Header["kid"] = claims.KeyID
	}
	signed, err := token.SignedString(key)
	if err != nil {
		t.Fatalf("sign token: %v", err)
	}
	return signed
}

func toMap(claims Claims) jwt.MapClaims {
	mapped := jwt.MapClaims{
		"iss":          claims.Issuer,
		"aud":          claims.Audience,
		"sub":          claims.Subject,
		"azp":          claims.AuthorizedParty,
		"realm_access": map[string][]string{"roles": claims.Roles},
	}
	if !claims.ExpiresAt.IsZero() {
		mapped["exp"] = claims.ExpiresAt.Unix()
	}
	if !claims.NotBefore.IsZero() {
		mapped["nbf"] = claims.NotBefore.Unix()
	}
	return mapped
}

func jwksBody(t testing.TB, public *rsa.PublicKey) []byte {
	t.Helper()
	encode := base64.RawURLEncoding.EncodeToString
	body, err := json.Marshal(map[string][]map[string]string{"keys": {{
		"kty": "RSA",
		"kid": KeyID,
		"use": "sig",
		"alg": "RS256",
		"n":   encode(public.N.Bytes()),
		"e":   encode(big.NewInt(int64(public.E)).Bytes()),
	}}})
	if err != nil {
		t.Fatalf("marshal jwks: %v", err)
	}
	return body
}
