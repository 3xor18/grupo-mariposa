package telemetry_test

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

func TestTraceIDRoundTrip(t *testing.T) {
	if telemetry.TraceID(context.Background()) != "" {
		t.Fatal("want empty trace id without value")
	}
	ctx := telemetry.WithTraceID(context.Background(), "abc")
	if telemetry.TraceID(ctx) != "abc" {
		t.Fatal("want trace id from context")
	}
}

func TestLoggerWritesContractFields(t *testing.T) {
	var buf bytes.Buffer
	logger := telemetry.NewLogger(&buf, slog.LevelInfo).With("k", "v")
	ctx := telemetry.WithTraceID(context.Background(), "trace-1")
	logger.InfoContext(ctx, "served", "status", 200)
	logger.DebugContext(ctx, "hidden")
	entry := decode(t, &buf)
	for _, key := range []string{"timestamp", "level", "service", "message", "traceId", "k"} {
		if _, ok := entry[key]; !ok {
			t.Fatalf("missing key %q in %v", key, entry)
		}
	}
	if entry["service"] != telemetry.ServiceName || entry["traceId"] != "trace-1" {
		t.Fatalf("unexpected entry %v", entry)
	}
}

func TestLoggerSupportsGroups(t *testing.T) {
	var buf bytes.Buffer
	telemetry.NewLogger(&buf, slog.LevelInfo).WithGroup("http").Info("served", "status", 200)
	if _, ok := decode(t, &buf)["http"]; !ok {
		t.Fatal("want grouped attributes")
	}
}

func TestLoggerOmitsTraceWhenAbsent(t *testing.T) {
	var buf bytes.Buffer
	telemetry.NewLogger(&buf, slog.LevelInfo).Info("started")
	if _, ok := decode(t, &buf)["traceId"]; ok {
		t.Fatal("trace id must be absent")
	}
}

func TestMetricsExposeRequests(t *testing.T) {
	metrics := telemetry.NewMetrics()
	metrics.ObserveRequest(http.MethodGet, "/products/{productId}", http.StatusOK, time.Millisecond)
	rec := httptest.NewRecorder()
	metrics.Handler().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/metrics", nil))
	body, _ := io.ReadAll(rec.Body)
	for _, want := range []string{
		`http_server_requests_total{method="GET",route="/products/{productId}",status="200"} 1`,
		"http_server_request_duration_seconds_bucket",
		"go_goroutines",
	} {
		if !strings.Contains(string(body), want) {
			t.Fatalf("metrics missing %q", want)
		}
	}
}

func decode(t *testing.T, buf *bytes.Buffer) map[string]any {
	t.Helper()
	var entry map[string]any
	if err := json.Unmarshal(buf.Bytes(), &entry); err != nil {
		t.Fatalf("decode log: %v (%s)", err, buf.String())
	}
	return entry
}
