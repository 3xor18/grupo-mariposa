package httpapi

import (
	"context"
	"errors"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/auth"
)

const (
	headerAuthorization = "Authorization"
	bearerPrefix        = "bearer "
	detailUnauthorized  = "A valid bearer token is required."
	detailForbidden     = "The token does not grant access to this resource."
	detailAuthDown      = "Authentication is temporarily unavailable."
	detailRateLimited   = "Request rate limit exceeded."
	logAuthRejected     = "authentication rejected"
	logAuthUnavailable  = "authentication unavailable"
	logAuthFailed       = "authentication failed"
)

type TokenVerifier interface {
	Verify(ctx context.Context, token, requiredRole string) (auth.Principal, error)
}

type KeyedLimiter interface {
	Delay(key string) time.Duration
}

type principalKey struct{}

func (rs responder) authenticate(verifier TokenVerifier, role string) middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			principal, err := verifier.Verify(r.Context(), bearerToken(r), role)
			if err != nil {
				rs.rejectAuth(w, r, err)
				return
			}
			ctx := context.WithValue(r.Context(), principalKey{}, principal.ID)
			next.ServeHTTP(w, r.WithContext(ctx))
		})
	}
}

func (rs responder) rejectAuth(w http.ResponseWriter, r *http.Request, err error) {
	switch {
	case errors.Is(err, auth.ErrForbidden):
		rs.logger.WarnContext(r.Context(), logAuthRejected, logKeyError, err.Error())
		rs.problem(w, r, kindForbidden, detailForbidden)
	case errors.Is(err, auth.ErrUnauthenticated):
		rs.logger.WarnContext(r.Context(), logAuthRejected, logKeyError, err.Error())
		w.Header().Set(headerAuthenticate, bearerChallenge)
		rs.problem(w, r, kindUnauthorized, detailUnauthorized)
	case errors.Is(err, auth.ErrUnavailable):
		rs.logger.ErrorContext(r.Context(), logAuthUnavailable, logKeyError, err.Error())
		rs.problem(w, r, kindUnavailable, detailAuthDown)
	default:
		rs.logger.ErrorContext(r.Context(), logAuthFailed, logKeyError, err.Error())
		rs.problem(w, r, kindInternal, detailInternal)
	}
}

func bearerToken(r *http.Request) string {
	header := r.Header.Get(headerAuthorization)
	prefixLength := len(bearerPrefix)
	if len(header) < prefixLength || !strings.EqualFold(header[:prefixLength], bearerPrefix) {
		return ""
	}
	return strings.TrimSpace(header[prefixLength:])
}

func (rs responder) limitBy(limiter KeyedLimiter, keyOf func(*http.Request) string) middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if wait := limiter.Delay(keyOf(r)); wait > 0 {
				setRetryAfter(w, wait)
				rs.problem(w, r, kindRateLimited, detailRateLimited)
				return
			}
			next.ServeHTTP(w, r)
		})
	}
}

func clientAddress(r *http.Request) string {
	host, _, err := net.SplitHostPort(r.RemoteAddr)
	if err != nil {
		return r.RemoteAddr
	}
	return host
}

func principalOf(r *http.Request) string {
	id, _ := r.Context().Value(principalKey{}).(string)
	return id
}
