package httpapi

import (
	"encoding/json"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	contentTypeHeader  = "Content-Type"
	contentTypeJSON    = "application/json"
	contentTypeProblem = "application/problem+json"
	retryAfterHeader   = "Retry-After"
	authenticateHeader = "WWW-Authenticate"
	bearerChallenge    = "Bearer"
	problemTypeBase    = "https://contracts.grupomariposa.dev/problems/"
	timestampLayout    = "2006-01-02T15:04:05.000Z07:00"
	codeWordSeparator  = "_"
	slugWordSeparator  = "-"
)

type Code string

const (
	CodeValidation         Code = "VALIDATION_ERROR"
	CodeProductNotFound    Code = "PRODUCT_NOT_FOUND"
	CodeResourceNotFound   Code = "RESOURCE_NOT_FOUND"
	CodeUnauthorized       Code = "UNAUTHORIZED"
	CodeForbidden          Code = "FORBIDDEN"
	CodeRateLimited        Code = "RATE_LIMITED"
	CodeInternal           Code = "INTERNAL_ERROR"
	CodeBadGateway         Code = "BAD_GATEWAY"
	CodeServiceUnavailable Code = "SERVICE_UNAVAILABLE"
)

type problemKind struct {
	status int
	code   Code
	title  string
}

var (
	kindValidation   = problemKind{http.StatusBadRequest, CodeValidation, "Validation failed"}
	kindNotFound     = problemKind{http.StatusNotFound, CodeProductNotFound, "Product not found"}
	kindNoRoute      = problemKind{http.StatusNotFound, CodeResourceNotFound, "Resource not found"}
	kindUnauthorized = problemKind{http.StatusUnauthorized, CodeUnauthorized, "Unauthorized"}
	kindForbidden    = problemKind{http.StatusForbidden, CodeForbidden, "Forbidden"}
	kindRateLimited  = problemKind{http.StatusTooManyRequests, CodeRateLimited, "Too many requests"}
	kindInternal     = problemKind{http.StatusInternalServerError, CodeInternal, "Internal error"}
	kindBadGateway   = problemKind{http.StatusBadGateway, CodeBadGateway, "Bad gateway"}
	kindUnavailable  = problemKind{
		http.StatusServiceUnavailable, CodeServiceUnavailable, "Service unavailable",
	}
)

type Problem struct {
	Type      string       `json:"type"`
	Title     string       `json:"title"`
	Status    int          `json:"status"`
	Code      Code         `json:"code"`
	Detail    string       `json:"detail"`
	Instance  string       `json:"instance"`
	TraceID   string       `json:"traceId"`
	Timestamp string       `json:"timestamp"`
	Errors    []FieldError `json:"errors,omitempty"`
}

type FieldError struct {
	Field   string `json:"field"`
	Message string `json:"message"`
}

type responder struct {
	clock  func() time.Time
	logger *slog.Logger
}

func (rs responder) problem(w http.ResponseWriter, r *http.Request, kind problemKind,
	detail string, fields ...FieldError,
) {
	body := Problem{
		Type:      problemTypeBase + slug(kind.code),
		Title:     kind.title,
		Status:    kind.status,
		Code:      kind.code,
		Detail:    detail,
		Instance:  r.URL.Path,
		TraceID:   telemetry.TraceID(r.Context()),
		Timestamp: rs.clock().UTC().Format(timestampLayout),
		Errors:    fields,
	}
	rs.write(w, r, kind.status, contentTypeProblem, body)
}

func (rs responder) json(w http.ResponseWriter, r *http.Request, status int, body any) {
	rs.write(w, r, status, contentTypeJSON, body)
}

func (rs responder) write(w http.ResponseWriter, r *http.Request, status int,
	contentType string, body any,
) {
	w.Header().Set(contentTypeHeader, contentType)
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil {
		rs.logger.WarnContext(r.Context(), logWriteFailed, logKeyError, err)
	}
}

func setRetryAfter(w http.ResponseWriter, seconds int) {
	w.Header().Set(retryAfterHeader, strconv.Itoa(seconds))
}

func slug(code Code) string {
	return strings.ReplaceAll(strings.ToLower(string(code)), codeWordSeparator, slugWordSeparator)
}
