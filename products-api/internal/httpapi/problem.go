package httpapi

import (
	"encoding/json"
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	headerContentType         = "Content-Type"
	headerCacheControl        = "Cache-Control"
	headerRetryAfter          = "Retry-After"
	headerAuthenticate        = "WWW-Authenticate"
	headerAllow               = "Allow"
	contentTypeJSON           = "application/json"
	contentTypeProblem        = "application/problem+json"
	cacheNoStore              = "no-store"
	bearerChallenge           = "Bearer"
	timestampLayout           = "2006-01-02T15:04:05.000Z07:00"
	codeWordSeparator         = "_"
	slugWordSeparator         = "-"
	statusClientClosedRequest = 499
	minRetryAfterSeconds      = 1
)

type code string

const (
	codeValidation         code = "VALIDATION_ERROR"
	codeProductNotFound    code = "PRODUCT_NOT_FOUND"
	codeResourceNotFound   code = "RESOURCE_NOT_FOUND"
	codeMethodNotAllowed   code = "METHOD_NOT_ALLOWED"
	codeUnauthorized       code = "UNAUTHORIZED"
	codeForbidden          code = "FORBIDDEN"
	codeRateLimited        code = "RATE_LIMITED"
	codeClientClosed       code = "CLIENT_CLOSED_REQUEST"
	codeInternal           code = "INTERNAL_ERROR"
	codeBadGateway         code = "BAD_GATEWAY"
	codeServiceUnavailable code = "SERVICE_UNAVAILABLE"
)

type problemKind int

const (
	kindValidation problemKind = iota
	kindNotFound
	kindNoRoute
	kindMethodNotAllowed
	kindUnauthorized
	kindForbidden
	kindRateLimited
	kindClientClosed
	kindInternal
	kindBadGateway
	kindUnavailable
	kindCount
)

type definition struct {
	status int
	code   code
	title  string
}

func definitions() [kindCount]definition {
	return [kindCount]definition{
		kindValidation:       {http.StatusBadRequest, codeValidation, "Validation failed"},
		kindNotFound:         {http.StatusNotFound, codeProductNotFound, "Product not found"},
		kindNoRoute:          {http.StatusNotFound, codeResourceNotFound, "Resource not found"},
		kindMethodNotAllowed: {http.StatusMethodNotAllowed, codeMethodNotAllowed, "Method not allowed"},
		kindUnauthorized:     {http.StatusUnauthorized, codeUnauthorized, "Unauthorized"},
		kindForbidden:        {http.StatusForbidden, codeForbidden, "Forbidden"},
		kindRateLimited:      {http.StatusTooManyRequests, codeRateLimited, "Too many requests"},
		kindClientClosed:     {statusClientClosedRequest, codeClientClosed, "Client closed request"},
		kindInternal:         {http.StatusInternalServerError, codeInternal, "Internal error"},
		kindBadGateway:       {http.StatusBadGateway, codeBadGateway, "Bad gateway"},
		kindUnavailable: {
			http.StatusServiceUnavailable, codeServiceUnavailable, "Service unavailable",
		},
	}
}

func (k problemKind) definition() definition {
	return definitions()[k]
}

type problem struct {
	Type      string       `json:"type"`
	Title     string       `json:"title"`
	Status    int          `json:"status"`
	Code      code         `json:"code"`
	Detail    string       `json:"detail"`
	Instance  string       `json:"instance"`
	TraceID   string       `json:"traceId"`
	Timestamp string       `json:"timestamp"`
	Errors    []fieldError `json:"errors,omitempty"`
}

type fieldError struct {
	Field   string `json:"field"`
	Message string `json:"message"`
}

type responder struct {
	clock    func() time.Time
	logger   *slog.Logger
	typeBase string
}

func (rs responder) problem(w http.ResponseWriter, r *http.Request, kind problemKind,
	detail string, fields ...fieldError,
) {
	d := kind.definition()
	body := problem{
		Type:      rs.typeBase + slug(d.code),
		Title:     d.title,
		Status:    d.status,
		Code:      d.code,
		Detail:    detail,
		Instance:  r.URL.Path,
		TraceID:   telemetry.TraceID(r.Context()),
		Timestamp: rs.clock().UTC().Format(timestampLayout),
		Errors:    fields,
	}
	w.Header().Set(headerCacheControl, cacheNoStore)
	rs.write(w, r, d.status, contentTypeProblem, body)
}

func (rs responder) json(w http.ResponseWriter, r *http.Request, status int, body any) {
	rs.write(w, r, status, contentTypeJSON, body)
}

func (rs responder) write(w http.ResponseWriter, r *http.Request, status int,
	contentType string, body any,
) {
	w.Header().Set(headerContentType, contentType)
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(body); err != nil {
		rs.logger.WarnContext(r.Context(), logWriteFailed, logKeyError, err.Error())
	}
}

func setRetryAfter(w http.ResponseWriter, wait time.Duration) {
	seconds := max(minRetryAfterSeconds, int(math.Ceil(wait.Seconds())))
	w.Header().Set(headerRetryAfter, strconv.Itoa(seconds))
}

func slug(c code) string {
	return strings.ReplaceAll(strings.ToLower(string(c)), codeWordSeparator, slugWordSeparator)
}
