package httpapi

import (
	"context"
	"errors"
	"math"
	"net/http"
	"strings"

	"golang.org/x/time/rate"

	"github.com/grupomariposa/platform/products-api/internal/auth"
)

const (
	authorizationHeader = "Authorization"
	bearerPrefix        = "bearer "
	detailUnauthorized  = "A valid bearer token is required."
	detailForbidden     = "The token does not grant access to this resource."
	detailRateLimited   = "Request rate limit exceeded."
	logAuthRejected     = "authentication rejected"
	logAuthFailed       = "authentication failed"
	minRetryAfterSecond = 1
)

type TokenVerifier interface {
	Verify(ctx context.Context, token string) error
}

type RateLimit struct {
	RPS   float64
	Burst int
}

func (rs responder) authenticate(verifier TokenVerifier) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			err := verifier.Verify(r.Context(), bearerToken(r))
			if err == nil {
				next.ServeHTTP(w, r)
				return
			}
			rs.rejectAuth(w, r, err)
		})
	}
}

func (rs responder) rejectAuth(w http.ResponseWriter, r *http.Request, err error) {
	switch {
	case errors.Is(err, auth.ErrForbidden):
		rs.logger.WarnContext(r.Context(), logAuthRejected, logKeyError, err)
		rs.problem(w, r, kindForbidden, detailForbidden)
	case errors.Is(err, auth.ErrUnauthenticated):
		rs.logger.WarnContext(r.Context(), logAuthRejected, logKeyError, err)
		w.Header().Set(authenticateHeader, bearerChallenge)
		rs.problem(w, r, kindUnauthorized, detailUnauthorized)
	default:
		rs.logger.ErrorContext(r.Context(), logAuthFailed, logKeyError, err)
		rs.problem(w, r, kindInternal, detailInternal)
	}
}

func bearerToken(r *http.Request) string {
	header := r.Header.Get(authorizationHeader)
	if len(header) < len(bearerPrefix) || !strings.EqualFold(header[:len(bearerPrefix)], bearerPrefix) {
		return ""
	}
	return strings.TrimSpace(header[len(bearerPrefix):])
}

func (rs responder) rateLimit(limit RateLimit) Middleware {
	limiter := rate.NewLimiter(rate.Limit(limit.RPS), limit.Burst)
	retryAfter := retryAfterSeconds(limit.RPS)
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if limiter.Allow() {
				next.ServeHTTP(w, r)
				return
			}
			setRetryAfter(w, retryAfter)
			rs.problem(w, r, kindRateLimited, detailRateLimited)
		})
	}
}

func retryAfterSeconds(rps float64) int {
	return max(minRetryAfterSecond, int(math.Ceil(1/rps)))
}
