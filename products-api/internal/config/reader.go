package config

import (
	"errors"
	"fmt"
	"log/slog"
	"math"
	"net/url"
	"strconv"
	"strings"
	"time"
)

const (
	floatBits = 64
	MaxInt    = math.MaxInt32
)

var (
	errOutOfRange     = errors.New("is out of range")
	errRequired       = errors.New("is required")
	errNotAbsoluteURL = errors.New("must be an absolute URL")
)

type LookupFunc func(key string) (string, bool)

type Reader struct {
	lookup LookupFunc
	errs   []error
}

func NewReader(lookup LookupFunc) *Reader {
	return &Reader{lookup: lookup}
}

func (r *Reader) Raw(key string) string {
	value, _ := r.lookup(key)
	return value
}

func (r *Reader) String(key, fallback string) string {
	if value := strings.TrimSpace(r.Raw(key)); value != "" {
		return value
	}
	return fallback
}

func (r *Reader) Int(key string, fallback, low, high int) int {
	raw := r.String(key, "")
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		r.Fail(key, err)
		return fallback
	}
	if value < low || value > high {
		r.Fail(key, errOutOfRange)
	}
	return value
}

func (r *Reader) Millis(key string, fallback int) time.Duration {
	return time.Duration(r.Int(key, fallback, 1, MaxInt)) * time.Millisecond
}

func (r *Reader) OptionalMillis(key string, fallback int) time.Duration {
	return time.Duration(r.Int(key, fallback, 0, MaxInt)) * time.Millisecond
}

func (r *Reader) PositiveFloat(key string, fallback float64) float64 {
	raw := r.String(key, "")
	if raw == "" {
		return fallback
	}
	value, err := strconv.ParseFloat(raw, floatBits)
	if err != nil {
		r.Fail(key, err)
		return fallback
	}
	if math.IsNaN(value) || value <= 0 || math.IsInf(value, 0) {
		r.Fail(key, errOutOfRange)
	}
	return value
}

func (r *Reader) Bool(key string, fallback bool) bool {
	raw := r.String(key, "")
	if raw == "" {
		return fallback
	}
	value, err := strconv.ParseBool(raw)
	if err != nil {
		r.Fail(key, err)
		return fallback
	}
	return value
}

func (r *Reader) LogLevel(key, fallback string) slog.Level {
	var level slog.Level
	if err := level.UnmarshalText([]byte(r.String(key, fallback))); err != nil {
		r.Fail(key, err)
	}
	return level
}

func (r *Reader) AbsoluteURL(key, fallback string) string {
	raw := r.String(key, fallback)
	if raw == "" {
		return raw
	}
	parsed, err := url.Parse(raw)
	if err != nil || !parsed.IsAbs() || parsed.Host == "" {
		r.Fail(key, errNotAbsoluteURL)
	}
	return raw
}

func (r *Reader) Require(key, value string) {
	if value == "" {
		r.Fail(key, errRequired)
	}
}

func (r *Reader) Fail(key string, err error) {
	r.errs = append(r.errs, fmt.Errorf("%s: %w", key, err))
}

func (r *Reader) Err() error {
	if err := errors.Join(r.errs...); err != nil {
		return fmt.Errorf("%w: %w", ErrInvalid, err)
	}
	return nil
}
