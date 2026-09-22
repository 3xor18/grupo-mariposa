package remote

import (
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

type failingBody struct{}

func (failingBody) Read([]byte) (int, error) { return 0, errors.New("connection reset") }

func testSettings(url string) settings {
	return settings{baseURL: url, appName: "products api", profile: "dev/1",
		timeout: time.Second, backoff: time.Millisecond, backoffMax: time.Millisecond}
}

func TestFetchEscapesPathSegments(t *testing.T) {
	var path string
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		path = r.URL.EscapedPath()
		w.Header().Set("Content-Type", plainText)
	}))
	defer server.Close()
	if _, err := newClient(testSettings(server.URL)).fetch(context.Background()); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if path != "/products%20api-dev%2F1.properties" {
		t.Fatalf("unexpected path %q", path)
	}
}

func TestFetchRejectsOversizedBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", plainText)
		_, _ = io.WriteString(w, strings.Repeat("a", 17))
	}))
	defer server.Close()
	c := newClient(testSettings(server.URL))
	c.maxBytes = 16
	if _, err := c.fetch(context.Background()); !errors.Is(err, errBodyTooLarge) {
		t.Fatalf("want body too large, got %v", err)
	}
	c.maxBytes = 17
	if _, err := c.fetch(context.Background()); err != nil {
		t.Fatalf("body at the limit must be accepted, got %v", err)
	}
}

func TestReadBodyFailureIsTransient(t *testing.T) {
	_, err := newClient(settings{}).readBody(failingBody{})
	if !errors.Is(err, errTransientFailure) {
		t.Fatalf("want transient failure, got %v", err)
	}
}

func TestFetchRejectsUnbuildableURL(t *testing.T) {
	c := newClient(settings{baseURL: "http://host\x7f", retries: 3, timeout: time.Second})
	if _, err := c.fetch(context.Background()); !errors.Is(err, errPermanentFailure) {
		t.Fatalf("want permanent failure, got %v", err)
	}
}

func TestJitterStaysWithinBounds(t *testing.T) {
	const delay = 100 * time.Millisecond
	for range 50 {
		if got := jitter(delay); got < delay/2 || got > delay {
			t.Fatalf("jitter %v outside [%v, %v]", got, delay/2, delay)
		}
	}
}

func TestBackoffIsCapped(t *testing.T) {
	var calls []time.Time
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		calls = append(calls, time.Now())
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer server.Close()
	s := testSettings(server.URL)
	s.retries, s.backoff, s.backoffMax = 3, 20*time.Millisecond, 20*time.Millisecond
	if _, err := newClient(s).fetch(context.Background()); !errors.Is(err, ErrUnavailable) {
		t.Fatalf("want unavailable, got %v", err)
	}
	for i := 1; i < len(calls); i++ {
		if gap := calls[i].Sub(calls[i-1]); gap > time.Second {
			t.Fatalf("backoff not capped: gap %v", gap)
		}
	}
}

func TestRedactedURL(t *testing.T) {
	if got := redactedURL("http://user:pass@config:8888"); got != "http://user:xxxxx@config:8888" {
		t.Fatalf("unexpected redaction %q", got)
	}
	if got := redactedURL("%zz://bad"); got != "" {
		t.Fatalf("want empty for unparsable url, got %q", got)
	}
}
