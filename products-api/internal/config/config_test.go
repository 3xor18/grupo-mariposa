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
	want := config.Config{
		Port:           8081,
		RequestTimeout: 3 * time.Second,
		Shutdown:       config.Shutdown{Timeout: 10 * time.Second, DrainDelay: 3 * time.Second},
		HTTP: config.HTTP{
			ReadHeaderTimeout: 5 * time.Second, ReadTimeout: 10 * time.Second,
			WriteTimeout: 15 * time.Second, IdleTimeout: time.Minute, MaxHeaderBytes: 16384,
		},
		RateLimit: config.RateLimit{RPS: 200, Burst: 400, MaxKeys: 10000},
		Auth: config.Auth{
			Enabled: true, Issuer: authEnv()[config.EnvAuthIssuer],
			JWKSURL: authEnv()[config.EnvAuthJWKSURL], RequiredRole: "products-reader",
			ClockLeeway: 30 * time.Second, JWKSTimeout: 2 * time.Second,
			JWKSRefresh: time.Hour, JWKSMinimumRefresh: 10 * time.Second,
		},
		Faults:          config.Faults{Timeout: 5 * time.Second},
		ProblemTypeBase: "https://contracts.grupomariposa.dev/problems/",
		LogLevel:        slog.LevelInfo,
	}
	assertConfig(t, cfg, want)
}

func TestLoadOverrides(t *testing.T) {
	cfg, err := config.Load(lookupFrom(overrideEnv()))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := config.Config{
		Port:           9000,
		RequestTimeout: 150 * time.Millisecond,
		Shutdown:       config.Shutdown{Timeout: 250 * time.Millisecond},
		HTTP: config.HTTP{
			ReadHeaderTimeout: time.Millisecond, ReadTimeout: 2 * time.Millisecond,
			WriteTimeout: time.Second, IdleTimeout: 4 * time.Millisecond, MaxHeaderBytes: 2048,
		},
		RateLimit: config.RateLimit{RPS: 0.5, Burst: 2, MaxKeys: 3},
		Auth: config.Auth{
			Audience: "products-api", RequiredRole: "products-reader",
			JWKSTimeout: 2 * time.Second, JWKSRefresh: time.Hour,
			JWKSMinimumRefresh: 10 * time.Second,
		},
		Faults: config.Faults{
			Enabled: true,
			Rules:   []fault.Rule{{ID: "PRD-012", Kind: fault.KindServiceUnavailable, Times: 2}},
			Timeout: 10 * time.Millisecond,
		},
		ProblemTypeBase: "https://errors.example/",
		LogLevel:        slog.LevelDebug,
	}
	assertConfig(t, cfg, want)
}

func overrideEnv() map[string]string {
	return map[string]string{
		config.EnvPort: "9000", config.EnvRequestTimeoutMS: "150",
		config.EnvShutdownTimeoutMS: "250", config.EnvShutdownDrainDelayMS: "0",
		config.EnvHTTPReadHeaderTimeout: "1", config.EnvHTTPReadTimeoutMS: "2",
		config.EnvHTTPWriteTimeoutMS: "1000", config.EnvHTTPIdleTimeoutMS: "4",
		config.EnvHTTPMaxHeaderBytes: "2048", config.EnvRateLimitRPS: "0.5",
		config.EnvRateLimitBurst: "2", config.EnvRateLimitMaxKeys: "3",
		config.EnvAuthEnabled: "false", config.EnvAuthAudience: "products-api",
		config.EnvAuthClockLeewayMS: "0", config.EnvFaultInjectionEnabled: "true",
		config.EnvFaultRules: "PRD-012:503:2", config.EnvFaultTimeoutMS: "10",
		config.EnvProblemTypeBaseURL: "https://errors.example/", config.EnvLogLevel: "debug",
	}
}

func assertConfig(t *testing.T, got, want config.Config) {
	t.Helper()
	assertRules(t, got.Faults.Rules, want.Faults.Rules)
	if got.Auth != want.Auth || got.Faults.Enabled != want.Faults.Enabled ||
		got.Faults.Timeout != want.Faults.Timeout {
		t.Fatalf("want %+v %+v, got %+v %+v", want.Auth, want.Faults, got.Auth, got.Faults)
	}
	gotScalars := []any{got.Port, got.RequestTimeout, got.Shutdown, got.HTTP, got.RateLimit,
		got.ProblemTypeBase, got.LogLevel}
	wantScalars := []any{want.Port, want.RequestTimeout, want.Shutdown, want.HTTP,
		want.RateLimit, want.ProblemTypeBase, want.LogLevel}
	for i := range wantScalars {
		if gotScalars[i] != wantScalars[i] {
			t.Fatalf("field %d: want %+v, got %+v", i, wantScalars[i], gotScalars[i])
		}
	}
}

func assertRules(t *testing.T, got, want []fault.Rule) {
	t.Helper()
	if len(got) != len(want) {
		t.Fatalf("want rules %+v, got %+v", want, got)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("rule %d: want %+v, got %+v", i, want[i], got[i])
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
		{name: "drain_negative", key: config.EnvShutdownDrainDelayMS, raw: "-1"},
		{name: "rps_negative", key: config.EnvRateLimitRPS, raw: "-1"},
		{name: "rps_not_a_number", key: config.EnvRateLimitRPS, raw: "NaN"},
		{name: "rps_not_numeric", key: config.EnvRateLimitRPS, raw: "fast"},
		{name: "burst_zero", key: config.EnvRateLimitBurst, raw: "0"},
		{name: "max_keys_zero", key: config.EnvRateLimitMaxKeys, raw: "0"},
		{name: "header_bytes_huge", key: config.EnvHTTPMaxHeaderBytes, raw: "99999999"},
		{name: "write_timeout_too_low", key: config.EnvHTTPWriteTimeoutMS, raw: "3000"},
		{name: "auth_not_boolean", key: config.EnvAuthEnabled, raw: "maybe"},
		{name: "faults_not_boolean", key: config.EnvFaultInjectionEnabled, raw: "maybe"},
		{name: "fault_unknown_type", key: config.EnvFaultRules, raw: "PRD-1:418"},
		{name: "log_level_unknown", key: config.EnvLogLevel, raw: "loud"},
		{name: "jwks_relative", key: config.EnvAuthJWKSURL, raw: "/relative"},
		{name: "problem_base_relative", key: config.EnvProblemTypeBaseURL, raw: "problems"},
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
		if !errors.Is(err, config.ErrInvalid) || !strings.Contains(err.Error(), key) {
			t.Fatalf("want error mentioning %s, got %v", key, err)
		}
	}
}

func TestLoadProbe(t *testing.T) {
	defaultTimeout := 2 * time.Second
	cases := []struct {
		name    string
		env     map[string]string
		want    config.Probe
		wantErr bool
	}{
		{name: "should_default_without_env", env: map[string]string{},
			want: config.Probe{Port: 8081, Timeout: defaultTimeout}},
		{name: "should_read_env", want: config.Probe{Port: 9090, Timeout: time.Second},
			env: map[string]string{config.EnvPort: "9090", config.EnvHealthcheckTimeoutMS: "1000"}},
		{name: "should_ignore_auth", want: config.Probe{Port: 8081, Timeout: defaultTimeout},
			env: map[string]string{config.EnvAuthJWKSURL: "/relative"}},
		{name: "should_reject_invalid", env: map[string]string{config.EnvPort: "0"},
			wantErr: true, want: config.Probe{Timeout: defaultTimeout}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			probe, err := config.LoadProbe(lookupFrom(tc.env))
			if tc.wantErr != errors.Is(err, config.ErrInvalid) || probe != tc.want {
				t.Fatalf("want %+v (err %v), got %+v %v", tc.want, tc.wantErr, probe, err)
			}
		})
	}
}

func TestEnvOnly(t *testing.T) {
	if !config.EnvOnly(config.EnvPort) || config.EnvOnly(config.EnvRateLimitRPS) {
		t.Fatal("only PORT is an environment-only key")
	}
}

func TestReaderRawKeepsWhitespace(t *testing.T) {
	r := config.NewReader(lookupFrom(map[string]string{"SECRET": " pass "}))
	if r.Raw("SECRET") != " pass " || r.Raw("MISSING") != "" || r.Err() != nil {
		t.Fatal("raw must return the value untouched")
	}
}
