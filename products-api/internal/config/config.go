package config

import (
	"errors"
	"log/slog"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/fault"
	"github.com/grupomariposa/platform/products-api/internal/market"
)

const (
	EnvPort                   = "PORT"
	EnvRequestTimeoutMS       = "REQUEST_TIMEOUT_MS"
	EnvShutdownTimeoutMS      = "SHUTDOWN_TIMEOUT_MS"
	EnvShutdownDrainDelayMS   = "SHUTDOWN_DRAIN_DELAY_MS"
	EnvHTTPReadHeaderTimeout  = "HTTP_READ_HEADER_TIMEOUT_MS"
	EnvHTTPReadTimeoutMS      = "HTTP_READ_TIMEOUT_MS"
	EnvHTTPWriteTimeoutMS     = "HTTP_WRITE_TIMEOUT_MS"
	EnvHTTPIdleTimeoutMS      = "HTTP_IDLE_TIMEOUT_MS"
	EnvHTTPMaxHeaderBytes     = "HTTP_MAX_HEADER_BYTES"
	EnvRateLimitRPS           = "RATE_LIMIT_RPS"
	EnvRateLimitBurst         = "RATE_LIMIT_BURST"
	EnvRateLimitMaxKeys       = "RATE_LIMIT_MAX_KEYS"
	EnvAuthEnabled            = "AUTH_ENABLED"
	EnvAuthIssuer             = "AUTH_ISSUER"
	EnvAuthAudience           = "AUTH_AUDIENCE"
	EnvAuthJWKSURL            = "AUTH_JWKS_URL"
	EnvAuthRequiredRole       = "AUTH_REQUIRED_ROLE"
	EnvAuthAdminRole          = "AUTH_ADMIN_ROLE"
	EnvAuthClockLeewayMS      = "AUTH_CLOCK_LEEWAY_MS"
	EnvAuthJWKSTimeoutMS      = "AUTH_JWKS_TIMEOUT_MS"
	EnvAuthJWKSRefreshMS      = "AUTH_JWKS_REFRESH_INTERVAL_MS"
	EnvAuthJWKSMinRefreshMS   = "AUTH_JWKS_MIN_REFRESH_INTERVAL_MS"
	EnvFaultInjectionEnabled  = "FAULT_INJECTION_ENABLED"
	EnvFaultRules             = "FAULT_RULES"
	EnvFaultTimeoutMS         = "FAULT_TIMEOUT_MS"
	EnvProblemTypeBaseURL     = "PROBLEM_TYPE_BASE_URL"
	EnvLogLevel               = "LOG_LEVEL"
	EnvHealthcheckTimeoutMS   = "HEALTHCHECK_TIMEOUT_MS"
	EnvAppEnvironment         = "APP_ENV"
	productionEnvironment     = "production"
	defaultAudience           = "products-api"
	defaultPort               = 8081
	defaultRequestTimeoutMS   = 3000
	defaultShutdownTimeoutMS  = 10000
	defaultDrainDelayMS       = 3000
	defaultReadHeaderMS       = 5000
	defaultReadTimeoutMS      = 10000
	defaultWriteTimeoutMS     = 15000
	defaultIdleTimeoutMS      = 60000
	defaultMaxHeaderBytes     = 16 << 10
	maxHeaderBytesLimit       = 1 << 20
	defaultRateLimitRPS       = 200
	defaultRateLimitBurst     = 400
	defaultRateLimitMaxKeys   = 10000
	defaultRequiredRole       = "products-reader"
	defaultAdminRole          = "products-admin"
	defaultClockLeewayMS      = 30000
	defaultJWKSTimeoutMS      = 2000
	defaultJWKSRefreshMS      = 3600000
	defaultJWKSMinRefreshMS   = 10000
	defaultFaultTimeoutMS     = 5000
	defaultProblemTypeBaseURL = "https://contracts.grupomariposa.dev/problems/"
	defaultLogLevel           = "INFO"
	defaultHealthcheckMS      = 2000
	minPort                   = 1
	maxPort                   = 65535
	minPositive               = 1
)

var (
	ErrInvalid            = errors.New("invalid configuration")
	errWriteTimeoutTooLow = errors.New("must be greater than " + EnvRequestTimeoutMS)
	errFaultsInProduction = errors.New("must not be true when " + EnvAppEnvironment + "=" +
		productionEnvironment)
)

type Config struct {
	Port            int
	RequestTimeout  time.Duration
	Shutdown        Shutdown
	HTTP            HTTP
	RateLimit       RateLimit
	Auth            Auth
	Faults          Faults
	Markets         market.Catalog
	Storage         Storage
	Kafka           Kafka
	Outbox          Outbox
	ProblemTypeBase string
	LogLevel        slog.Level
}

type Shutdown struct {
	Timeout    time.Duration
	DrainDelay time.Duration
}

type HTTP struct {
	ReadHeaderTimeout time.Duration
	ReadTimeout       time.Duration
	WriteTimeout      time.Duration
	IdleTimeout       time.Duration
	MaxHeaderBytes    int
}

type RateLimit struct {
	RPS     float64
	Burst   int
	MaxKeys int
}

type Auth struct {
	Enabled            bool
	Issuer             string
	Audience           string
	JWKSURL            string
	RequiredRole       string
	AdminRole          string
	ClockLeeway        time.Duration
	JWKSTimeout        time.Duration
	JWKSRefresh        time.Duration
	JWKSMinimumRefresh time.Duration
}

type Faults struct {
	Enabled bool
	Rules   []fault.Rule
	Timeout time.Duration
}

type Probe struct {
	Port    int
	Timeout time.Duration
}

func EnvOnly(key string) bool {
	return key == EnvPort
}

func Load(lookup LookupFunc) (Config, error) {
	r := NewReader(lookup)
	cfg := Config{
		Port:            r.Int(EnvPort, defaultPort, minPort, maxPort),
		RequestTimeout:  r.Millis(EnvRequestTimeoutMS, defaultRequestTimeoutMS),
		Shutdown:        loadShutdown(r),
		HTTP:            loadHTTP(r),
		RateLimit:       loadRateLimit(r),
		Auth:            loadAuth(r),
		Faults:          loadFaults(r),
		Markets:         loadMarkets(r),
		Storage:         loadStorage(r),
		Outbox:          loadOutbox(r),
		ProblemTypeBase: r.AbsoluteURL(EnvProblemTypeBaseURL, defaultProblemTypeBaseURL),
		LogLevel:        r.LogLevel(EnvLogLevel, defaultLogLevel),
	}
	cfg.Kafka = loadKafka(r, cfg.Storage)
	if cfg.HTTP.WriteTimeout <= cfg.RequestTimeout {
		r.Fail(EnvHTTPWriteTimeoutMS, errWriteTimeoutTooLow)
	}
	if err := r.Err(); err != nil {
		return Config{}, err
	}
	return cfg, nil
}

func LoadProbe(lookup LookupFunc) (Probe, error) {
	r := NewReader(lookup)
	probe := Probe{
		Port:    r.Int(EnvPort, defaultPort, minPort, maxPort),
		Timeout: r.Millis(EnvHealthcheckTimeoutMS, defaultHealthcheckMS),
	}
	return probe, r.Err()
}

func loadShutdown(r *Reader) Shutdown {
	return Shutdown{
		Timeout:    r.Millis(EnvShutdownTimeoutMS, defaultShutdownTimeoutMS),
		DrainDelay: r.OptionalMillis(EnvShutdownDrainDelayMS, defaultDrainDelayMS),
	}
}

func loadHTTP(r *Reader) HTTP {
	return HTTP{
		ReadHeaderTimeout: r.Millis(EnvHTTPReadHeaderTimeout, defaultReadHeaderMS),
		ReadTimeout:       r.Millis(EnvHTTPReadTimeoutMS, defaultReadTimeoutMS),
		WriteTimeout:      r.Millis(EnvHTTPWriteTimeoutMS, defaultWriteTimeoutMS),
		IdleTimeout:       r.Millis(EnvHTTPIdleTimeoutMS, defaultIdleTimeoutMS),
		MaxHeaderBytes: r.Int(EnvHTTPMaxHeaderBytes, defaultMaxHeaderBytes,
			minPositive, maxHeaderBytesLimit),
	}
}

func loadRateLimit(r *Reader) RateLimit {
	return RateLimit{
		RPS:     r.PositiveFloat(EnvRateLimitRPS, defaultRateLimitRPS),
		Burst:   r.Int(EnvRateLimitBurst, defaultRateLimitBurst, minPositive, MaxInt),
		MaxKeys: r.Int(EnvRateLimitMaxKeys, defaultRateLimitMaxKeys, minPositive, MaxInt),
	}
}

func loadAuth(r *Reader) Auth {
	auth := Auth{
		Enabled:            r.Bool(EnvAuthEnabled, true),
		Issuer:             r.String(EnvAuthIssuer, ""),
		Audience:           r.String(EnvAuthAudience, defaultAudience),
		JWKSURL:            r.AbsoluteURL(EnvAuthJWKSURL, ""),
		RequiredRole:       r.String(EnvAuthRequiredRole, defaultRequiredRole),
		AdminRole:          r.String(EnvAuthAdminRole, defaultAdminRole),
		ClockLeeway:        r.OptionalMillis(EnvAuthClockLeewayMS, defaultClockLeewayMS),
		JWKSTimeout:        r.Millis(EnvAuthJWKSTimeoutMS, defaultJWKSTimeoutMS),
		JWKSRefresh:        r.Millis(EnvAuthJWKSRefreshMS, defaultJWKSRefreshMS),
		JWKSMinimumRefresh: r.Millis(EnvAuthJWKSMinRefreshMS, defaultJWKSMinRefreshMS),
	}
	if auth.Enabled {
		r.Require(EnvAuthIssuer, auth.Issuer)
		r.Require(EnvAuthJWKSURL, auth.JWKSURL)
		r.RejectBlank(EnvAuthAudience)
	}
	return auth
}

func loadFaults(r *Reader) Faults {
	rules, err := fault.ParseRules(r.String(EnvFaultRules, ""))
	if err != nil {
		r.Fail(EnvFaultRules, err)
	}
	enabled := r.Bool(EnvFaultInjectionEnabled, false)
	if enabled && production(r) {
		r.Fail(EnvFaultInjectionEnabled, errFaultsInProduction)
	}
	return Faults{
		Enabled: enabled,
		Rules:   rules,
		Timeout: r.Millis(EnvFaultTimeoutMS, defaultFaultTimeoutMS),
	}
}
