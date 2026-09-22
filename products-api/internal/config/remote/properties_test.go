package remote_test

import (
	"errors"
	"strings"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/config/remote"
)

const springBody = `# comment
! bang comment

rate-limit.rps: 200
auth.jwks-url: http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs
fault.rules=PRD-012:503:2,PRD-013:timeout
escaped\:key\=name = a\\b\:c\=d
spaced   value with spaces
tabs=one\ttwo\nthree
unicode=caf\u00e9
multi.line = first,\
    second
management.endpoints.web.exposure.include: health
empty.value=
lonely.key`

func TestParseProperties(t *testing.T) {
	got, err := remote.ParseProperties(strings.NewReader(springBody))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := map[string]string{
		"rate-limit.rps":   "200",
		"auth.jwks-url":    "http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs",
		"fault.rules":      "PRD-012:503:2,PRD-013:timeout",
		"escaped:key=name": `a\b:c=d`,
		"spaced":           "value with spaces",
		"tabs":             "one\ttwo\nthree",
		"unicode":          "café",
		"multi.line":       "first,second",
		"management.endpoints.web.exposure.include": "health",
		"empty.value": "",
		"lonely.key":  "",
	}
	if len(got) != len(want) {
		t.Fatalf("want %d properties, got %d: %v", len(want), len(got), got)
	}
	for key, value := range want {
		if got[key] != value {
			t.Errorf("%s: want %q, got %q", key, value, got[key])
		}
	}
}

func TestParsePropertiesRejectsMalformedInput(t *testing.T) {
	cases := map[string]string{
		"short_unicode_in_value": "key=\\u00",
		"bad_hex_in_value":       "key=\\uzzzz",
		"bad_unicode_in_key":     "k\\uzzzz=v",
		"line_too_long":          "key=" + strings.Repeat("x", 70*1024),
	}
	for name, body := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := remote.ParseProperties(strings.NewReader(body)); err == nil {
				t.Fatal("want parse error")
			}
		})
	}
}

func TestParsePropertiesKeepsPendingContinuationAtEOF(t *testing.T) {
	got, err := remote.ParseProperties(strings.NewReader("key=value\\"))
	if err != nil || got["key"] != "value" {
		t.Fatalf("want value, got %v err=%v", got, err)
	}
}

func TestEnvKey(t *testing.T) {
	cases := map[string]string{
		"rate-limit.rps":         "RATE_LIMIT_RPS",
		"fault.rules":            "FAULT_RULES",
		"auth.jwks-url":          "AUTH_JWKS_URL",
		" request-timeout-ms ":   "REQUEST_TIMEOUT_MS",
		"PORT":                   "PORT",
		"auth.required-role":     "AUTH_REQUIRED_ROLE",
		"shutdown.timeout.ms":    "SHUTDOWN_TIMEOUT_MS",
		"fault.timeout-ms":       "FAULT_TIMEOUT_MS",
		"rate-limit.burst":       "RATE_LIMIT_BURST",
		"log.level":              "LOG_LEVEL",
		"auth.enabled":           "AUTH_ENABLED",
		"auth.issuer":            "AUTH_ISSUER",
		"request.timeout.ms":     "REQUEST_TIMEOUT_MS",
		"shutdown-timeout-ms":    "SHUTDOWN_TIMEOUT_MS",
		"fault-timeout-ms":       "FAULT_TIMEOUT_MS",
		"management.server.port": "MANAGEMENT_SERVER_PORT",
	}
	for property, want := range cases {
		if got := remote.EnvKey(property); got != want {
			t.Errorf("%q: want %q, got %q", property, want, got)
		}
	}
}

func TestMalformedUnicodeIsWrapped(t *testing.T) {
	_, err := remote.ParseProperties(strings.NewReader("key=\\uzzzz"))
	if err == nil || errors.Unwrap(err) == nil {
		t.Fatalf("want wrapped error, got %v", err)
	}
}
