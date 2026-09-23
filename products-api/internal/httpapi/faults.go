package httpapi

import (
	"context"
	"net/http"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/fault"
)

const (
	detailInjected   = "Injected fault for resilience testing."
	logFaultInjected = "fault injected"
	logKeyFault      = "fault"
	faultRetryAfter  = time.Second
)

type FaultInjector interface {
	Next(id string) (fault.Kind, bool)
}

func faultProblem(kind fault.Kind) problemKind {
	switch kind {
	case fault.KindBadRequest:
		return kindValidation
	case fault.KindTooManyRequests:
		return kindRateLimited
	case fault.KindBadGateway:
		return kindBadGateway
	case fault.KindServiceUnavailable:
		return kindUnavailable
	default:
		return kindInternal
	}
}

func (rs responder) injectFault(injector FaultInjector, hold time.Duration) middleware {
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
			rs.failWith(w, r, faultProblem(kind))
		})
	}
}

func (rs responder) failWith(w http.ResponseWriter, r *http.Request, kind problemKind) {
	if kind == kindRateLimited {
		setRetryAfter(w, faultRetryAfter)
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

func deadline(timeout time.Duration) middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			ctx, cancel := context.WithTimeout(r.Context(), timeout)
			defer cancel()
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}
