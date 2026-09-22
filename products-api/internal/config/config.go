package config

import (
	"errors"
	"fmt"
	"log/slog"
	"net/url"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/fault"
)

const (
	EnvPort              = "PORT"
	EnvRequestTimeoutMS  = "REQUEST_TIMEOUT_MS"
	EnvShutdownTimeoutMS = "SHUTDOWN_TIMEOUT_MS"
	EnvRateLimitRPS      = "RATE_LIMIT_RPS"
	EnvRateLimitBurst    = "RATE_LIMIT_BURST"
	EnvAuthEnabled       = "AUTH_ENABLED"
	EnvAuthIssuer        = "AUTH_ISSUER"
	EnvAuthJWKSURL       = "AUTH_JWKS_URL"
	EnvAuthRequiredRole  = "AUTH_REQUIRED_ROLE"
	EnvFaultRules        = "FAULT_RULES"
	EnvFaultTimeoutMS    = "FAULT_TIMEOUT_MS"
	EnvLogLevel          = "LOG_LEVEL"
)

const (
	defaultPort              = 8081
	defaultRequestTimeoutMS  = 3000
	defaultShutdownTimeoutMS = 10000
	defaultRateLimitRPS      = 200
	defaultRateLimitBurst    = 400
	defaultFaultTimeoutMS    = 5000
	defaultRequiredRole      = "products-reader"
	defaultLogLevel          = "INFO"
	minPort                  = 1
	maxPort                  = 65535
	minPositive              = 1
)

var ErrInvalid = errors.New("invalid configuration")

type LookupFunc func(key string) (string, bool)

type Config struct {
	Port            int
	RequestTimeout  time.Duration
	ShutdownTimeout time.Duration
	RateLimit       RateLimit
	Auth            Auth
	Faults          Faults
	LogLevel        slog.Level
}

type RateLimit struct {
	RPS   float64
	Burst int
}

type Auth struct {
	Enabled      bool
	Issuer       string
	JWKSURL      string
	RequiredRole string
}

type Faults struct {
	Rules   []fault.Rule
	Timeout time.Duration
}

func Load(lookup LookupFunc) (Config, error) {
	r := &reader{lookup: lookup}
	cfg := Config{
		Port:            r.intInRange(EnvPort, defaultPort, minPort, maxPort),
		RequestTimeout:  r.millis(EnvRequestTimeoutMS, defaultRequestTimeoutMS),
		ShutdownTimeout: r.millis(EnvShutdownTimeoutMS, defaultShutdownTimeoutMS),
		RateLimit: RateLimit{
			RPS:   r.positiveFloat(EnvRateLimitRPS, defaultRateLimitRPS),
			Burst: r.intInRange(EnvRateLimitBurst, defaultRateLimitBurst, minPositive, maxInt),
		},
		Auth:     loadAuth(r),
		Faults:   loadFaults(r),
		LogLevel: r.logLevel(EnvLogLevel, defaultLogLevel),
	}
	if err := errors.Join(r.errs...); err != nil {
		return Config{}, fmt.Errorf("%w: %w", ErrInvalid, err)
	}
	return cfg, nil
}

func loadAuth(r *reader) Auth {
	auth := Auth{
		Enabled:      r.boolean(EnvAuthEnabled, true),
		Issuer:       r.text(EnvAuthIssuer, ""),
		JWKSURL:      r.text(EnvAuthJWKSURL, ""),
		RequiredRole: r.text(EnvAuthRequiredRole, defaultRequiredRole),
	}
	if !auth.Enabled {
		return auth
	}
	r.require(EnvAuthIssuer, auth.Issuer)
	r.absoluteURL(EnvAuthJWKSURL, auth.JWKSURL)
	return auth
}

func loadFaults(r *reader) Faults {
	rules, err := fault.ParseRules(r.text(EnvFaultRules, ""))
	if err != nil {
		r.fail(EnvFaultRules, err)
	}
	return Faults{Rules: rules, Timeout: r.millis(EnvFaultTimeoutMS, defaultFaultTimeoutMS)}
}

func (r *reader) absoluteURL(key, raw string) {
	parsed, err := url.Parse(raw)
	if err != nil || !parsed.IsAbs() || parsed.Host == "" {
		r.fail(key, errNotAbsoluteURL)
	}
}
