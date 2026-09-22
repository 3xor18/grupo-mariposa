package auth

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/MicahParks/jwkset"
	"github.com/golang-jwt/jwt/v5"
)

type failingStorage struct {
	keyStorage
}

func (failingStorage) KeyReplaceAll(context.Context, []jwkset.JWK) error {
	return errors.New("storage full")
}

const testKeyBits = 2048

func serveJWKS(t *testing.T, status int, body string) string {
	t.Helper()
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(status)
		_, _ = io.WriteString(w, body)
	}))
	t.Cleanup(server.Close)
	return server.URL
}

func testStore(url string) *keyStore {
	return newKeyStore(url, time.Second, time.Hour, slog.New(slog.DiscardHandler))
}

func TestRefreshFailures(t *testing.T) {
	cases := map[string]string{
		"should_fail_on_status":     serveJWKS(t, http.StatusBadGateway, ""),
		"should_fail_on_bad_json":   serveJWKS(t, http.StatusOK, "{"),
		"should_fail_on_bad_url":    "http://host\x7f",
		"should_fail_on_no_network": "http://127.0.0.1:1",
	}
	for name, url := range cases {
		t.Run(name, func(t *testing.T) {
			store := testStore(url)
			if err := store.refresh(context.Background()); err == nil || store.warmed.Load() {
				t.Fatalf("want refresh failure, got %v", err)
			}
		})
	}
}

func TestRefreshSkipsInvalidKeysAndStoreFailures(t *testing.T) {
	url := serveJWKS(t, http.StatusOK, `{"keys":[{"kty":"RSA","kid":"bad","n":"!","e":"AQAB"}]}`)
	store := testStore(url)
	if err := store.refresh(context.Background()); err != nil || !store.warmed.Load() {
		t.Fatalf("invalid keys must be skipped, got %v", err)
	}
	store = testStore(url)
	store.store = failingStorage{keyStorage: store.store}
	if err := store.refresh(context.Background()); err == nil {
		t.Fatal("want storage error")
	}
}

func TestFindDoesNotRefreshAgainWhenRateLimited(t *testing.T) {
	store := testStore(serveJWKS(t, http.StatusOK, `{"keys":[]}`))
	if _, err := store.find(context.Background(), "a"); !errors.Is(err, jwkset.ErrKeyNotFound) {
		t.Fatalf("want key not found after refresh, got %v", err)
	}
	if _, err := store.find(context.Background(), "a"); !errors.Is(err, jwkset.ErrKeyNotFound) {
		t.Fatalf("want key not found without refresh, got %v", err)
	}
}

func TestKeyfuncRejectsAlgorithmMismatch(t *testing.T) {
	key, err := rsa.GenerateKey(rand.Reader, testKeyBits)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	encode := base64.RawURLEncoding.EncodeToString
	body := fmt.Sprintf(`{"keys":[{"kty":"RSA","kid":"k","alg":"RS512","n":%q,"e":"AQAB"}]}`,
		encode(key.N.Bytes()))
	store := testStore(serveJWKS(t, http.StatusOK, body))
	token := &jwt.Token{Header: map[string]any{headerKeyID: "k"}, Method: jwt.SigningMethodRS256}
	if _, err := store.keyfunc(context.Background())(token); !errors.Is(err,
		errAlgorithmMismatch) {
		t.Fatalf("want algorithm mismatch, got %v", err)
	}
}
