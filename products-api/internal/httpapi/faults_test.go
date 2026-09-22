package httpapi_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/fault"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
)

func withFaults(t *testing.T, raw string, hold time.Duration) func(*httpapi.Dependencies) {
	t.Helper()
	rules, err := fault.ParseRules(raw)
	if err != nil {
		t.Fatalf("rules: %v", err)
	}
	return func(d *httpapi.Dependencies) {
		d.Faults = fault.NewInjector(rules)
		d.FaultHold = hold
	}
}

func TestFaultInjectionStatuses(t *testing.T) {
	h := newHarness(t, withFaults(t,
		"PRD-001:400,PRD-002:429,PRD-003:500,PRD-004:502,PRD-008:503", time.Second))
	cases := []struct {
		target string
		status int
		code   httpapi.Code
	}{
		{"/products/PRD-001?market=MX", http.StatusBadRequest, httpapi.CodeValidation},
		{"/products/PRD-002?market=MX", http.StatusTooManyRequests, httpapi.CodeRateLimited},
		{"/products/PRD-003?market=MX", http.StatusInternalServerError, httpapi.CodeInternal},
		{"/products/PRD-004?market=MX", http.StatusBadGateway, httpapi.CodeBadGateway},
		{"/products/PRD-008?market=MX", http.StatusServiceUnavailable,
			httpapi.CodeServiceUnavailable},
	}
	for _, tc := range cases {
		t.Run(tc.target, func(t *testing.T) {
			rec := h.get(t, tc.target)
			problem := assertProblem(t, rec, tc.status, tc.code)
			if problem.Detail != "Injected fault for resilience testing." {
				t.Fatalf("unexpected detail %q", problem.Detail)
			}
			wantRetry := ""
			if tc.status == http.StatusTooManyRequests {
				wantRetry = "1"
			}
			if got := rec.Header().Get("Retry-After"); got != wantRetry {
				t.Fatalf("want Retry-After %q, got %q", wantRetry, got)
			}
		})
	}
}

func TestFaultInjectionRecoversAfterTimes(t *testing.T) {
	h := newHarness(t, withFaults(t, "PRD-012:503:2", time.Second))
	want := []int{http.StatusServiceUnavailable, http.StatusServiceUnavailable, http.StatusOK}
	for i, status := range want {
		if rec := h.get(t, "/products/PRD-012?market=MX"); rec.Code != status {
			t.Fatalf("call %d: want %d, got %d", i, status, rec.Code)
		}
	}
	if rec := h.get(t, productPath); rec.Code != http.StatusOK {
		t.Fatal("other ids must not be affected")
	}
}

func TestFaultInjectionDisabledByDefault(t *testing.T) {
	h := newHarness(t)
	if rec := h.get(t, "/products/PRD-012?market=MX"); rec.Code != http.StatusOK {
		t.Fatalf("without injector the product must be served, got %d", rec.Code)
	}
}

func TestFaultTimeoutShorterThanDeadlineDelaysResponse(t *testing.T) {
	const hold = 30 * time.Millisecond
	h := newHarness(t, withFaults(t, "PRD-013:timeout", hold))
	started := time.Now()
	rec := h.get(t, "/products/PRD-013?market=MX")
	if rec.Code != http.StatusOK || time.Since(started) < hold {
		t.Fatalf("want delayed 200, got %d after %v", rec.Code, time.Since(started))
	}
}

func TestFaultTimeoutLongerThanDeadlineReturns503(t *testing.T) {
	const timeout = 30 * time.Millisecond
	h := newHarness(t, withFaults(t, "PRD-013:timeout", time.Minute),
		func(d *httpapi.Dependencies) { d.RequestTimeout = timeout })
	started := time.Now()
	assertProblem(t, h.get(t, "/products/PRD-013?market=MX"), http.StatusServiceUnavailable,
		httpapi.CodeServiceUnavailable)
	if elapsed := time.Since(started); elapsed > defaultTimeout {
		t.Fatalf("deadline must bound the fault hold, took %v", elapsed)
	}
}

func TestFaultTimeoutStopsWhenClientCancels(t *testing.T) {
	h := newHarness(t, withFaults(t, "PRD-013:timeout", time.Minute))
	ctx, cancel := context.WithCancel(context.Background())
	req := httptest.NewRequestWithContext(ctx, http.MethodGet, "/products/PRD-013?market=MX", nil)
	done := make(chan *httptest.ResponseRecorder, 1)
	go func() { done <- h.do(t, req) }()
	cancel()
	select {
	case rec := <-done:
		assertProblem(t, rec, httpapi.StatusClientClosed, httpapi.CodeClientClosed)
	case <-time.After(5 * time.Second):
		t.Fatal("fault timeout ignored client cancellation")
	}
}
