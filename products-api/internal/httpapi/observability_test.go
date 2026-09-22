package httpapi_test

import (
	"encoding/json"
	"net/http"
	"regexp"
	"strings"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/httpapi"
)

var (
	traceIDPattern     = regexp.MustCompile(`^[0-9a-f]{32}$`)
	traceparentPattern = regexp.MustCompile(`^00-([0-9a-f]{32})-[0-9a-f]{16}-([0-9a-f]{2})$`)
)

const incomingTraceID = "4bf92f3577b34da6a3ce929d0e0e4736"

func TestTraceparentIsPropagated(t *testing.T) {
	h := newHarness(t)
	header := "00-" + incomingTraceID + "-00f067aa0ba902b7-00"
	rec := h.get(t, "/products/PRD-999?market=MX", "traceparent", header)
	groups := traceparentPattern.FindStringSubmatch(rec.Header().Get("traceparent"))
	if groups == nil || groups[1] != incomingTraceID || groups[2] != "00" {
		t.Fatalf("unexpected traceparent %q", rec.Header().Get("traceparent"))
	}
	if decodeProblem(t, rec).TraceID != incomingTraceID {
		t.Fatal("problem must carry the incoming trace id")
	}
	if rec.Header().Get("X-Request-Id") != incomingTraceID {
		t.Fatal("request id must default to the trace id")
	}
}

func TestInvalidTraceparentIsReplaced(t *testing.T) {
	h := newHarness(t)
	invalid := []string{
		"",
		"garbage",
		"ff-" + incomingTraceID + "-00f067aa0ba902b7-01",
		"00-00000000000000000000000000000000-00f067aa0ba902b7-01",
		"00-" + incomingTraceID + "-0000000000000000-01",
	}
	for _, header := range invalid {
		rec := h.get(t, "/products/PRD-999?market=MX", "traceparent", header)
		traceID := decodeProblem(t, rec).TraceID
		if !traceIDPattern.MatchString(traceID) || traceID == incomingTraceID {
			t.Fatalf("header %q: want generated trace id, got %q", header, traceID)
		}
	}
}

func TestRequestIDIsEchoedWhenValid(t *testing.T) {
	h := newHarness(t)
	if got := h.get(t, "/health/live", "X-Request-Id", "req-123").Header().Get(
		"X-Request-Id"); got != "req-123" {
		t.Fatalf("valid request id must be echoed, got %q", got)
	}
	for _, invalid := range []string{"bad id\n", strings.Repeat("a", 129)} {
		got := h.get(t, "/health/live", "X-Request-Id", invalid).Header().Get("X-Request-Id")
		if !traceIDPattern.MatchString(got) {
			t.Fatalf("invalid request id must be replaced, got %q", got)
		}
	}
}

func TestAccessLogAndMetrics(t *testing.T) {
	h := newHarness(t)
	h.get(t, productPath, "traceparent", "00-"+incomingTraceID+"-00f067aa0ba902b7-01")
	got := h.recorder.last()
	want := observation{method: http.MethodGet, route: httpapi.RouteProduct, status: 200}
	if got != want {
		t.Fatalf("want observation %+v, got %+v", want, got)
	}
	entry := lastLogEntry(t, h.logs.String())
	wantFields := map[string]any{
		"service": "products-api", "traceId": incomingTraceID, "method": "GET",
		"path": "/products/PRD-001", "route": httpapi.RouteProduct, "status": float64(200),
		"message": "request served", "requestId": incomingTraceID,
	}
	for key, value := range wantFields {
		if entry[key] != value {
			t.Fatalf("access log %s: want %v, got %v", key, value, entry[key])
		}
	}
	if _, ok := entry["durationMs"].(float64); !ok {
		t.Fatalf("access log must include a numeric duration: %v", entry)
	}
}

func TestMethodIsNormalizedForMetricsAndLogs(t *testing.T) {
	h := newHarness(t)
	cases := map[string]string{
		http.MethodGet: http.MethodGet, http.MethodPost: http.MethodPost,
		http.MethodPut: http.MethodPut, http.MethodPatch: http.MethodPatch,
		http.MethodDelete: http.MethodDelete, http.MethodOptions: http.MethodOptions,
		http.MethodHead: http.MethodHead, "BREW": "OTHER", "get": "OTHER",
		"X" + strings.Repeat("Y", 64): "OTHER",
	}
	for method, want := range cases {
		h.do(t, newRequest(t, method, "/health/live"))
		if got := h.recorder.last().method; got != want {
			t.Fatalf("%q: want metric method %q, got %q", method, want, got)
		}
		if got := lastLogEntry(t, h.logs.String())["method"]; got != want {
			t.Fatalf("%q: want logged method %q, got %v", method, want, got)
		}
	}
}

func TestSecurityHeadersOnEveryResponse(t *testing.T) {
	h := newHarness(t)
	for _, target := range []string{productPath, "/products/PRD-999?market=MX", "/health/live",
		"/metrics", "/unknown"} {
		if got := h.get(t, target).Header().Get("X-Content-Type-Options"); got != "nosniff" {
			t.Fatalf("%s: want nosniff, got %q", target, got)
		}
	}
}

func TestHealthEndpoints(t *testing.T) {
	cases := []struct {
		name   string
		ready  bool
		target string
		status int
		body   string
	}{
		{name: "live", ready: false, target: "/health/live", status: 200, body: "UP"},
		{name: "ready", ready: true, target: "/health/ready", status: 200, body: "UP"},
		{name: "not_ready", ready: false, target: "/health/ready", status: 503, body: "DOWN"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			h := newHarness(t, func(d *httpapi.Dependencies) { d.Readiness = readiness{tc.ready} })
			rec := h.get(t, tc.target)
			var body map[string]string
			if err := json.Unmarshal(rec.Body.Bytes(), &body); err != nil {
				t.Fatalf("decode: %v", err)
			}
			if rec.Code != tc.status || body["status"] != tc.body || len(body) != 1 {
				t.Fatalf("want %d %s, got %d %v", tc.status, tc.body, rec.Code, body)
			}
		})
	}
}

func lastLogEntry(t *testing.T, logs string) map[string]any {
	t.Helper()
	lines := strings.Split(strings.TrimSpace(logs), "\n")
	var entry map[string]any
	if err := json.Unmarshal([]byte(lines[len(lines)-1]), &entry); err != nil {
		t.Fatalf("decode log line: %v", err)
	}
	return entry
}
