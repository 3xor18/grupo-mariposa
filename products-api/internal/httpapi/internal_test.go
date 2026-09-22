package httpapi

import (
	"bytes"
	"errors"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

type failingWriter struct {
	*httptest.ResponseRecorder
}

func (f *failingWriter) Write([]byte) (int, error) {
	return 0, errors.New("connection reset")
}

type countingRecorder struct {
	route string
}

func (c *countingRecorder) ObserveRequest(_, route string, _ int, _ time.Duration) {
	c.route = route
}

func probe(t *testing.T, target string) *http.Request {
	t.Helper()
	return httptest.NewRequestWithContext(t.Context(), http.MethodGet, target, nil)
}

func testResponder(logs *bytes.Buffer) responder {
	return responder{clock: time.Now, logger: slog.New(slog.NewJSONHandler(logs, nil))}
}

func TestWriteFailureIsLogged(t *testing.T) {
	var logs bytes.Buffer
	rs := testResponder(&logs)
	req := probe(t, "/health/live")
	rs.live(&failingWriter{ResponseRecorder: httptest.NewRecorder()}, req)
	if !strings.Contains(logs.String(), logWriteFailed) {
		t.Fatal("write failure must be logged")
	}
}

func TestStatusRecorderKeepsFirstStatus(t *testing.T) {
	inner := httptest.NewRecorder()
	rec := &statusRecorder{ResponseWriter: inner, status: http.StatusOK}
	_, _ = rec.Write([]byte("x"))
	rec.WriteHeader(http.StatusTeapot)
	if rec.status != http.StatusOK || rec.Unwrap() != inner {
		t.Fatalf("unexpected recorder state %+v", rec)
	}
}

func TestObserveLabelsUnmatchedRequests(t *testing.T) {
	var logs bytes.Buffer
	counter := &countingRecorder{}
	handler := testResponder(&logs).observe(counter)(http.NotFoundHandler())
	handler.ServeHTTP(httptest.NewRecorder(), probe(t, "/x"))
	if counter.route != unmatchedRoute {
		t.Fatalf("want %q, got %q", unmatchedRoute, counter.route)
	}
}

func TestRecoverPanicRethrowsAbort(t *testing.T) {
	var logs bytes.Buffer
	handler := testResponder(&logs).recoverPanic(http.HandlerFunc(
		func(http.ResponseWriter, *http.Request) { panic(http.ErrAbortHandler) }))
	defer func() {
		if err, _ := recover().(error); !errors.Is(err, http.ErrAbortHandler) {
			t.Fatal("abort handler panic must propagate")
		}
	}()
	handler.ServeHTTP(httptest.NewRecorder(), probe(t, "/"))
}
