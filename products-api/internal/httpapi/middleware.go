package httpapi

import (
	"errors"
	"fmt"
	"net/http"
	"runtime/debug"
	"time"
)

const (
	headerContentTypeOptions = "X-Content-Type-Options"
	noSniff                  = "nosniff"
	logKeyError              = "error"
	logKeyMethod             = "method"
	logKeyPath               = "path"
	logKeyRoute              = "route"
	logKeyStatus             = "status"
	logKeyDurationMS         = "durationMs"
	logKeyRequestID          = "requestId"
	logKeyStack              = "stack"
	logRequestServed         = "request served"
	logPanic                 = "panic recovered"
	logPanicAfterHeader      = "panic recovered after response started"
	logWriteFailed           = "response write failed"
	unmatchedRoute           = "unmatched"
	otherMethod              = "OTHER"
)

type middleware func(http.Handler) http.Handler

type RequestRecorder interface {
	ObserveRequest(method, route string, status int, elapsed time.Duration)
}

func chain(h http.Handler, middlewares ...middleware) http.Handler {
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

func newStatusRecorder(w http.ResponseWriter) *statusRecorder {
	return &statusRecorder{ResponseWriter: w, status: http.StatusOK}
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

func secureHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set(headerContentTypeOptions, noSniff)
		next.ServeHTTP(w, r)
	})
}

func (rs responder) observe(recorder RequestRecorder) middleware {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			started := time.Now()
			rec := newStatusRecorder(w)
			next.ServeHTTP(rec, r)
			elapsed := time.Since(started)
			method, route := normalizeMethod(r.Method), routeOf(r)
			recorder.ObserveRequest(method, route, rec.status, elapsed)
			rs.logger.InfoContext(r.Context(), logRequestServed,
				logKeyMethod, method,
				logKeyPath, r.URL.Path,
				logKeyRoute, route,
				logKeyStatus, rec.status,
				logKeyDurationMS, float64(elapsed)/float64(time.Millisecond),
				logKeyRequestID, requestID(r.Context()),
			)
		})
	}
}

func normalizeMethod(method string) string {
	switch method {
	case http.MethodGet, http.MethodHead, http.MethodPost, http.MethodPut,
		http.MethodPatch, http.MethodDelete, http.MethodOptions:
		return method
	default:
		return otherMethod
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
		tracker := newStatusRecorder(w)
		defer func() {
			recovered := recover()
			if recovered == nil {
				return
			}
			if err, ok := recovered.(error); ok && errors.Is(err, http.ErrAbortHandler) {
				panic(recovered)
			}
			rs.handlePanic(tracker, r, recovered)
		}()
		next.ServeHTTP(tracker, r)
	})
}

func (rs responder) handlePanic(w *statusRecorder, r *http.Request, recovered any) {
	message := logPanic
	if w.wroteHeader {
		message = logPanicAfterHeader
	}
	rs.logger.ErrorContext(r.Context(), message,
		logKeyError, fmt.Sprint(recovered), logKeyStack, string(debug.Stack()))
	if !w.wroteHeader {
		rs.problem(w, r, kindInternal, detailInternal)
	}
}
