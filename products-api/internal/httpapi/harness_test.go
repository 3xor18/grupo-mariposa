package httpapi_test

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"sync"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/fault"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
	"github.com/grupomariposa/platform/products-api/internal/product"
	"github.com/grupomariposa/platform/products-api/internal/storage/memory"
	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	productPath      = "/products/PRD-001?market=MX"
	fixedTimestamp   = "2026-09-22T10:00:00.000Z"
	generousRPS      = 1e6
	generousBurst    = 1e6
	defaultTimeout   = time.Second
	defaultFaultHold = 10 * time.Millisecond
)

var fixedTime = time.Date(2026, 9, 22, 10, 0, 0, 0, time.UTC)

type readiness struct{ ready bool }

func (r readiness) Ready() bool { return r.ready }

type finderFunc func(ctx context.Context, q catalog.Query) (product.Product, error)

func (f finderFunc) GetProduct(ctx context.Context, q catalog.Query) (product.Product, error) {
	return f(ctx, q)
}

type observation struct {
	method, route string
	status        int
}

type recorder struct {
	mu   sync.Mutex
	seen []observation
}

func (r *recorder) ObserveRequest(method, route string, status int, _ time.Duration) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.seen = append(r.seen, observation{method: method, route: route, status: status})
}

func (r *recorder) last() observation {
	r.mu.Lock()
	defer r.mu.Unlock()
	return r.seen[len(r.seen)-1]
}

type harness struct {
	handler  http.Handler
	logs     *syncBuffer
	recorder *recorder
}

type syncBuffer struct {
	mu  sync.Mutex
	buf bytes.Buffer
}

func (s *syncBuffer) Write(p []byte) (int, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.buf.Write(p)
}

func (s *syncBuffer) String() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.buf.String()
}

func defaultDependencies(logs io.Writer, rec *recorder) httpapi.Dependencies {
	return httpapi.Dependencies{
		Products:       catalog.NewService(memory.NewSeededRepository()),
		Faults:         fault.NewInjector(nil),
		FaultHold:      defaultFaultHold,
		RateLimit:      httpapi.RateLimit{RPS: generousRPS, Burst: generousBurst},
		RequestTimeout: defaultTimeout,
		Readiness:      readiness{ready: true},
		Recorder:       rec,
		MetricsHandler: telemetry.NewMetrics().Handler(),
		Logger:         telemetry.NewLogger(logs, slog.LevelDebug),
		Clock:          func() time.Time { return fixedTime },
	}
}

func newHarness(t *testing.T, customize ...func(*httpapi.Dependencies)) harness {
	t.Helper()
	logs, rec := &syncBuffer{}, &recorder{}
	deps := defaultDependencies(logs, rec)
	for _, apply := range customize {
		apply(&deps)
	}
	return harness{handler: httpapi.NewHandler(deps), logs: logs, recorder: rec}
}

func (h harness) get(t *testing.T, target string, headers ...string) *httptest.ResponseRecorder {
	t.Helper()
	return h.do(t, httptest.NewRequest(http.MethodGet, target, nil), headers...)
}

func (h harness) do(t *testing.T, req *http.Request, headers ...string) *httptest.ResponseRecorder {
	t.Helper()
	for i := 0; i+1 < len(headers); i += 2 {
		req.Header.Set(headers[i], headers[i+1])
	}
	rec := httptest.NewRecorder()
	h.handler.ServeHTTP(rec, req)
	return rec
}

func decodeProblem(t *testing.T, rec *httptest.ResponseRecorder) httpapi.Problem {
	t.Helper()
	if got := rec.Header().Get("Content-Type"); got != "application/problem+json" {
		t.Fatalf("want problem content type, got %q (%s)", got, rec.Body.String())
	}
	var problem httpapi.Problem
	if err := json.Unmarshal(rec.Body.Bytes(), &problem); err != nil {
		t.Fatalf("decode problem: %v", err)
	}
	return problem
}

func assertProblem(t *testing.T, rec *httptest.ResponseRecorder, status int, code httpapi.Code) {
	t.Helper()
	if rec.Code != status {
		t.Fatalf("want status %d, got %d (%s)", status, rec.Code, rec.Body.String())
	}
	problem := decodeProblem(t, rec)
	if problem.Status != status || problem.Code != code || problem.Timestamp != fixedTimestamp {
		t.Fatalf("unexpected problem %+v", problem)
	}
	if problem.TraceID == "" || problem.Title == "" || problem.Detail == "" {
		t.Fatalf("incomplete problem %+v", problem)
	}
}
