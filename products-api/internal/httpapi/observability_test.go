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
	rec := h.get(t, "/health/live", "X-Request-Id", "req-123")
	if rec.Header().Get("X-Request-Id") != "req-123" {
		t.Fatal("valid request id must be echoed")
	}
	rec = h.get(t, "/health/live", "X-Request-Id", "bad id\n")
	if !traceIDPattern.MatchString(rec.Header().Get("X-Request-Id")) {
		t.Fatal("invalid request id must be replaced")
	}
}

func TestAccessLogAndMetrics(t *testing.T) {
	h := newHarness(t)
	h.get(t, productPath, "traceparent", "00-"+incomingTraceID+"-00f067aa0ba902b7-01")
	got := h.recorder.last()
	if got.method != http.MethodGet || got.route != httpapi.RouteProduct || got.status != 200 {
		t.Fatalf("unexpected observation %+v", got)
	}
	entry := lastLogEntry(t, h.logs.String())
	for _, key := range []string{"service", "traceId", "method", "path", "status", "durationMs"} {
		if _, ok := entry[key]; !ok {
			t.Fatalf("access log missing %q: %v", key, entry)
		}
	}
	if entry["traceId"] != incomingTraceID || entry["path"] != "/products/PRD-001" {
		t.Fatalf("unexpected access log %v", entry)
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
			_ = json.Unmarshal(rec.Body.Bytes(), &body)
			if rec.Code != tc.status || body["status"] != tc.body {
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
