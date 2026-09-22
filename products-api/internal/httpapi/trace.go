package httpapi

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"net/http"
	"regexp"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	traceparentHeader  = "traceparent"
	requestIDHeader    = "X-Request-Id"
	traceparentVersion = "00"
	defaultTraceFlags  = "01"
	traceparentSep     = "-"
	traceIDBytes       = 16
	spanIDBytes        = 8
	invalidVersion     = "ff"
	maxRequestIDLength = 128
	traceIDGroup       = 2
	flagsGroup         = 4
)

var (
	traceparentPattern = regexp.MustCompile(
		`^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$`)
	requestIDPattern = regexp.MustCompile(`^[A-Za-z0-9._:\-]+$`)
	zeroTraceID      = strings.Repeat("0", traceIDBytes*2)
	zeroSpanID       = strings.Repeat("0", spanIDBytes*2)
)

type requestIDKey struct{}

func traceContext(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		traceID, flags := parseTraceparent(r.Header.Get(traceparentHeader))
		reqID := sanitizeRequestID(r.Header.Get(requestIDHeader), traceID)
		ctx := telemetry.WithTraceID(r.Context(), traceID)
		ctx = context.WithValue(ctx, requestIDKey{}, reqID)
		w.Header().Set(traceparentHeader, strings.Join(
			[]string{traceparentVersion, traceID, randomHex(spanIDBytes), flags}, traceparentSep))
		w.Header().Set(requestIDHeader, reqID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func parseTraceparent(header string) (traceID, flags string) {
	groups := traceparentPattern.FindStringSubmatch(strings.TrimSpace(header))
	if groups == nil || !validTraceparent(groups) {
		return randomHex(traceIDBytes), defaultTraceFlags
	}
	return groups[traceIDGroup], groups[flagsGroup]
}

func validTraceparent(groups []string) bool {
	const versionGroup, spanGroup = 1, 3
	return groups[versionGroup] != invalidVersion &&
		groups[traceIDGroup] != zeroTraceID &&
		groups[spanGroup] != zeroSpanID
}

func sanitizeRequestID(header, fallback string) string {
	if len(header) == 0 || len(header) > maxRequestIDLength || !requestIDPattern.MatchString(header) {
		return fallback
	}
	return header
}

func requestID(ctx context.Context) string {
	id, _ := ctx.Value(requestIDKey{}).(string)
	return id
}

func randomHex(size int) string {
	buf := make([]byte, size)
	_, _ = rand.Read(buf)
	return hex.EncodeToString(buf)
}
