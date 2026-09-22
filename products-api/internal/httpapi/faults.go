package httpapi

import (
	"context"
	"net/http"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/fault"
)

const (
	detailInjected     = "Injected fault for resilience testing."
	logFaultInjected   = "fault injected"
	logKeyFault        = "fault"
	faultRetryAfterSec = 1
)

type FaultInjector interface {
	Next(id string) (fault.Kind, bool)
}

var faultProblems = map[fault.Kind]problemKind{
	fault.KindBadRequest:         kindValidation,
	fault.KindTooManyRequests:    kindRateLimited,
	fault.KindInternalError:      kindInternal,
	fault.KindBadGateway:         kindBadGateway,
	fault.KindServiceUnavailable: kindUnavailable,
}

func (rs responder) injectFault(injector FaultInjector, hold time.Duration) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			kind, ok := injector.Next(r.PathValue(pathProductID))
			if !ok {
				next.ServeHTTP(w, r)
				return
			}
			rs.logger.InfoContext(r.Context(), logFaultInjected, logKeyFault, string(kind))
			if kind == fault.KindTimeout {
				wait(r.Context(), hold)
				next.ServeHTTP(w, r)
				return
			}
			rs.failWith(w, r, faultProblems[kind])
		})
	}
}

func (rs responder) failWith(w http.ResponseWriter, r *http.Request, kind problemKind) {
	if kind.status == http.StatusTooManyRequests {
		setRetryAfter(w, faultRetryAfterSec)
	}
	rs.problem(w, r, kind, detailInjected)
}

func wait(ctx context.Context, hold time.Duration) {
	timer := time.NewTimer(hold)
	defer timer.Stop()
	select {
	case <-ctx.Done():
	case <-timer.C:
	}
}

func deadline(timeout time.Duration) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ctx, cancel := context.WithTimeout(r.Context(), timeout)
			defer cancel()
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}
