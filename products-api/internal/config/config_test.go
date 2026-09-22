package config_test

import (
	"errors"
	"log/slog"
	"strings"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/fault"
)

func lookupFrom(env map[string]string) config.LookupFunc {
	return func(key string) (string, bool) {
		value, ok := env[key]
		return value, ok
	}
}

func authEnv() map[string]string {
	return map[string]string{
		config.EnvAuthIssuer:  "http://localhost:8180/realms/mariposa",
		config.EnvAuthJWKSURL: "http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs",
	}
}

func TestLoadDefaults(t *testing.T) {
	cfg, err := config.Load(lookupFrom(authEnv()))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	checks := map[string]bool{
		"port":     cfg.Port == 8081,
		"request":  cfg.RequestTimeout == 3*time.Second,
		"shutdown": cfg.ShutdownTimeout == 10*time.Second,
		"rps":      cfg.RateLimit.RPS == 200,
		"burst":    cfg.RateLimit.Burst == 400,
		"auth":     cfg.Auth.Enabled && cfg.Auth.RequiredRole == "products-reader",
		"faults":   len(cfg.Faults.Rules) == 0 && cfg.Faults.Timeout == 5*time.Second,
		"level":    cfg.LogLevel == slog.LevelInfo,
	}
	for name, ok := range checks {
		if !ok {
			t.Errorf("unexpected default for %s: %+v", name, cfg)
		}
	}
}

func TestLoadOverrides(t *testing.T) {
	env := map[string]string{
		config.EnvPort:              "9000",
		config.EnvRequestTimeoutMS:  "150",
		config.EnvShutdownTimeoutMS: "250",
		config.EnvRateLimitRPS:      "0.5",
		config.EnvRateLimitBurst:    "2",
		config.EnvAuthEnabled:       "false",
		config.EnvFaultRules:        "PRD-012:503:2",
		config.EnvFaultTimeoutMS:    "10",
		config.EnvLogLevel:          "debug",
	}
	cfg, err := config.Load(lookupFrom(env))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := fault.Rule{ID: "PRD-012", Kind: fault.KindServiceUnavailable, Times: 2}
	checks := map[string]bool{
		"port":     cfg.Port == 9000,
		"request":  cfg.RequestTimeout == 150*time.Millisecond,
		"shutdown": cfg.ShutdownTimeout == 250*time.Millisecond,
		"rate":     cfg.RateLimit == config.RateLimit{RPS: 0.5, Burst: 2},
		"auth":     !cfg.Auth.Enabled,
		"faults":   cfg.Faults.Rules[0] == want && cfg.Faults.Timeout == 10*time.Millisecond,
		"level":    cfg.LogLevel == slog.LevelDebug,
	}
	for name, ok := range checks {
		if !ok {
			t.Errorf("override not applied for %s: %+v", name, cfg)
		}
	}
}

func TestLoadRejectsInvalidValues(t *testing.T) {
	cases := []struct {
		name string
		key  string
		raw  string
	}{
		{name: "port_out_of_range", key: config.EnvPort, raw: "70000"},
		{name: "timeout_not_numeric", key: config.EnvRequestTimeoutMS, raw: "abc"},
		{name: "rps_negative", key: config.EnvRateLimitRPS, raw: "-1"},
		{name: "rps_not_a_number", key: config.EnvRateLimitRPS, raw: "NaN"},
		{name: "rps_not_numeric", key: config.EnvRateLimitRPS, raw: "fast"},
		{name: "burst_zero", key: config.EnvRateLimitBurst, raw: "0"},
		{name: "auth_not_boolean", key: config.EnvAuthEnabled, raw: "maybe"},
		{name: "fault_unknown_type", key: config.EnvFaultRules, raw: "PRD-1:418"},
		{name: "log_level_unknown", key: config.EnvLogLevel, raw: "loud"},
		{name: "jwks_relative", key: config.EnvAuthJWKSURL, raw: "/relative"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			env := authEnv()
			env[tc.key] = tc.raw
			_, err := config.Load(lookupFrom(env))
			if !errors.Is(err, config.ErrInvalid) || !strings.Contains(err.Error(), tc.key) {
				t.Fatalf("want ErrInvalid mentioning %s, got %v", tc.key, err)
			}
		})
	}
}

func TestLoadRequiresAuthSettingsWhenEnabled(t *testing.T) {
	_, err := config.Load(lookupFrom(map[string]string{}))
	for _, key := range []string{config.EnvAuthIssuer, config.EnvAuthJWKSURL} {
		if err == nil || !strings.Contains(err.Error(), key) {
			t.Fatalf("want error mentioning %s, got %v", key, err)
		}
	}
}
