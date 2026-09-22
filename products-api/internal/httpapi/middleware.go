package httpapi

import (
	"errors"
	"fmt"
	"net/http"
	"runtime/debug"
	"time"
)

const (
	logKeyError      = "error"
	logKeyMethod     = "method"
	logKeyPath       = "path"
	logKeyRoute      = "route"
	logKeyStatus     = "status"
	logKeyDurationMS = "durationMs"
	logKeyRequestID  = "requestId"
	logKeyStack      = "stack"
	logRequestServed = "request served"
	logPanic         = "panic recovered"
	logWriteFailed   = "response write failed"
	unmatchedRoute   = "unmatched"
)

type Middleware func(http.Handler) http.Handler

type RequestRecorder interface {
	ObserveRequest(method, route string, status int, elapsed time.Duration)
}

func chain(h http.Handler, middlewares ...Middleware) http.Handler {
	for i := len(middlewares) - 1; i >= 0; i-- {
		h = middlewares[i](h)
	}
	return h
}

type statusRecorder struct {
	http.ResponseWriter
	status      int
	wroteHeader bool
}

func (s *statusRecorder) WriteHeader(status int) {
	if !s.wroteHeader {
		s.status, s.wroteHeader = status, true
	}
	s.ResponseWriter.WriteHeader(status)
}

func (s *statusRecorder) Write(body []byte) (int, error) {
	if !s.wroteHeader {
		s.WriteHeader(http.StatusOK)
	}
	return s.ResponseWriter.Write(body)
}

func (s *statusRecorder) Unwrap() http.ResponseWriter {
	return s.ResponseWriter
}

func (rs responder) observe(recorder RequestRecorder) Middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			started := time.Now()
			rec := &statusRecorder{ResponseWriter: w, status: http.StatusOK}
			next.ServeHTTP(rec, r)
			elapsed := time.Since(started)
			route := routeOf(r)
			recorder.ObserveRequest(r.Method, route, rec.status, elapsed)
			rs.logger.InfoContext(r.Context(), logRequestServed,
				logKeyMethod, r.Method,
				logKeyPath, r.URL.Path,
				logKeyRoute, route,
				logKeyStatus, rec.status,
				logKeyDurationMS, float64(elapsed)/float64(time.Millisecond),
				logKeyRequestID, requestID(r.Context()),
			)
		})
	}
}

func routeOf(r *http.Request) string {
	if r.Pattern == "" {
		return unmatchedRoute
	}
	return r.Pattern
}

func (rs responder) recoverPanic(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		defer func() {
			recovered := recover()
			if recovered == nil {
				return
			}
			if err, ok := recovered.(error); ok && errors.Is(err, http.ErrAbortHandler) {
				panic(recovered)
			}
			rs.logger.ErrorContext(r.Context(), logPanic,
				logKeyError, fmt.Sprint(recovered), logKeyStack, string(debug.Stack()))
			rs.problem(w, r, kindInternal, detailInternal)
		}()
		next.ServeHTTP(w, r)
	})
}
