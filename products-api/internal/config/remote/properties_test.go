package remote

import (
	"strings"
	"testing"
)

const springBody = "# comment\n" +
	"! bang comment\n" +
	"\n" +
	"rate-limit.rps: 200\n" +
	"auth.jwks-url: http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs\n" +
	"fault.rules=PRD-012:503:2,PRD-013:timeout\n" +
	`escaped\:key\=name = a\\b\:c\=d` + "\n" +
	"spaced   value with spaces \t\n" +
	`kept.trailing = value\ ` + "\n" +
	`tabs=one\ttwo\nthree\rfour\ffive` + "\n" +
	`unicode=caf\u00e9` + "\n" +
	`emoji=\uD83D\uDE00` + "\n" +
	`lonely.surrogate=\uD83Dx` + "\n" +
	"multi.line = first,\\\n" +
	"    second\n" +
	"\f  form.feed.indent=yes\n" +
	"management.endpoints.web.exposure.include: health\n" +
	"empty.value=\n" +
	"lonely.key\n"

func TestParseProperties(t *testing.T) {
	got, err := parseProperties(strings.NewReader(springBody))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := map[string]string{
		"rate-limit.rps":   "200",
		"auth.jwks-url":    "http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs",
		"fault.rules":      "PRD-012:503:2,PRD-013:timeout",
		"escaped:key=name": `a\b:c=d`,
		"spaced":           "value with spaces",
		"kept.trailing":    "value ",
		"tabs":             "one\ttwo\nthree\rfour\ffive",
		"unicode":          "café",
		"emoji":            "\U0001F600",
		"lonely.surrogate": "\uFFFDx",
		"multi.line":       "first,second",
		"form.feed.indent": "yes",
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
			if _, err := parseProperties(strings.NewReader(body)); err == nil {
				t.Fatal("want parse error")
			}
		})
	}
}

func TestParsePropertiesKeepsPendingContinuationAtEOF(t *testing.T) {
	got, err := parseProperties(strings.NewReader("key=value\\"))
	if err != nil || len(got) != 1 || got["key"] != "value" {
		t.Fatalf("want value, got %v err=%v", got, err)
	}
}

func TestUnescapeKeepsLoneTrailingBackslash(t *testing.T) {
	got, err := unescape([]rune(`end\`))
	if err != nil || got != `end\` {
		t.Fatalf("want literal backslash, got %q err=%v", got, err)
	}
}

func TestEnvKey(t *testing.T) {
	cases := []struct{ property, want string }{
		{"rate-limit.rps", "RATE_LIMIT_RPS"},
		{"fault.rules", "FAULT_RULES"},
		{"auth.jwks-url", "AUTH_JWKS_URL"},
		{" request-timeout-ms	", "REQUEST_TIMEOUT_MS"},
		{"fault.injection-enabled", "FAULT_INJECTION_ENABLED"},
		{"auth.audience", "AUTH_AUDIENCE"},
		{"management.server.port", "MANAGEMENT_SERVER_PORT"},
	}
	for _, tc := range cases {
		if got := envKey(tc.property); got != tc.want {
			t.Errorf("%q: want %q, got %q", tc.property, tc.want, got)
		}
	}
}
