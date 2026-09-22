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
	headerTraceparent  = "traceparent"
	headerRequestID    = "X-Request-Id"
	traceparentVersion = "00"
	defaultTraceFlags  = "01"
	traceparentSep     = "-"
	traceIDBytes       = 16
	spanIDBytes        = 8
	hexCharsPerByte    = 2
	invalidVersion     = "ff"
	maxRequestIDLength = 128
	zeroHexDigit       = "0"
	versionGroup       = 1
	traceIDGroup       = 2
	spanGroup          = 3
	flagsGroup         = 4
)

var (
	traceparentPattern = regexp.MustCompile(
		`^([0-9a-f]{2})-([0-9a-f]{32})-([0-9a-f]{16})-([0-9a-f]{2})$`)
	requestIDPattern = regexp.MustCompile(`^[A-Za-z0-9._:\-]+$`)
)

type requestIDKey struct{}

func traceContext(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		traceID, flags := parseTraceparent(r.Header.Get(headerTraceparent))
		reqID := sanitizeRequestID(r.Header.Get(headerRequestID), traceID)
		ctx := telemetry.WithTraceID(r.Context(), traceID)
		ctx = context.WithValue(ctx, requestIDKey{}, reqID)
		w.Header().Set(headerTraceparent, strings.Join(
			[]string{traceparentVersion, traceID, randomHex(spanIDBytes), flags}, traceparentSep))
		w.Header().Set(headerRequestID, reqID)
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
	return groups[versionGroup] != invalidVersion &&
		groups[traceIDGroup] != zeros(traceIDBytes) &&
		groups[spanGroup] != zeros(spanIDBytes)
}

func zeros(bytes int) string {
	return strings.Repeat(zeroHexDigit, bytes*hexCharsPerByte)
}

func sanitizeRequestID(header, fallback string) string {
	if header == "" || len(header) > maxRequestIDLength || !requestIDPattern.MatchString(header) {
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
