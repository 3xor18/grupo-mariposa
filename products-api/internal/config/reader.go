package config

import (
	"errors"
	"fmt"
	"log/slog"
	"math"
	"strconv"
	"strings"
	"time"
)

const (
	maxInt    = math.MaxInt32
	floatBits = 64
)

var (
	errOutOfRange     = errors.New("is out of range")
	errRequired       = errors.New("is required")
	errNotAbsoluteURL = errors.New("must be an absolute URL")
)

type reader struct {
	lookup LookupFunc
	errs   []error
}

func (r *reader) text(key, fallback string) string {
	value, ok := r.lookup(key)
	value = strings.TrimSpace(value)
	if !ok || value == "" {
		return fallback
	}
	return value
}

func (r *reader) intInRange(key string, fallback, low, high int) int {
	raw := r.text(key, "")
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		r.fail(key, err)
		return fallback
	}
	if value < low || value > high {
		r.fail(key, errOutOfRange)
	}
	return value
}

func (r *reader) millis(key string, fallback int) time.Duration {
	return time.Duration(r.intInRange(key, fallback, minPositive, maxInt)) * time.Millisecond
}

func (r *reader) positiveFloat(key string, fallback float64) float64 {
	raw := r.text(key, "")
	if raw == "" {
		return fallback
	}
	value, err := strconv.ParseFloat(raw, floatBits)
	if err != nil {
		r.fail(key, err)
		return fallback
	}
	if math.IsNaN(value) || value <= 0 || math.IsInf(value, 0) {
		r.fail(key, errOutOfRange)
	}
	return value
}

func (r *reader) boolean(key string, fallback bool) bool {
	raw := r.text(key, "")
	if raw == "" {
		return fallback
	}
	value, err := strconv.ParseBool(raw)
	if err != nil {
		r.fail(key, err)
		return fallback
	}
	return value
}

func (r *reader) logLevel(key, fallback string) slog.Level {
	var level slog.Level
	if err := level.UnmarshalText([]byte(r.text(key, fallback))); err != nil {
		r.fail(key, err)
	}
	return level
}

func (r *reader) require(key, value string) {
	if value == "" {
		r.fail(key, errRequired)
	}
}

func (r *reader) fail(key string, err error) {
	r.errs = append(r.errs, fmt.Errorf("%s: %w", key, err))
}
