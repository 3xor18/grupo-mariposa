package remote

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/config"
)

const (
	testUser     = "config-user"
	testPassword = "s3cr3t-pass"
	testSecret   = "super-secret-token-value"
	fastBackoff  = "1"
	plainText    = "text/plain;charset=UTF-8"
	remoteBody   = "rate-limit.rps: 50\n" +
		"fault.rules: PRD-012:503:2\n" +
		"auth.jwks-url: http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs\n" +
		"auth.client-secret: " + testSecret + "\n" +
		"auth.issuer: http://localhost:8180/realms/mariposa\n" +
		"port: 9999\n"
)

type configServer struct {
	*httptest.Server
	calls       atomic.Int32
	failures    int32
	status      int
	contentType string
	body        string
	path        atomic.Value
	auth        atomic.Value
}

func newConfigServer(t *testing.T, failures int32, status int) *configServer {
	t.Helper()
	s := &configServer{failures: failures, status: status, contentType: plainText,
		body: remoteBody}
	s.Server = httptest.NewServer(http.HandlerFunc(s.serve))
	t.Cleanup(s.Close)
	return s
}

func (s *configServer) serve(w http.ResponseWriter, r *http.Request) {
	s.path.Store(r.URL.EscapedPath())
	user, pass, _ := r.BasicAuth()
	s.auth.Store(user + ":" + pass)
	if s.calls.Add(1) <= s.failures {
		w.WriteHeader(s.status)
		return
	}
	w.Header().Set("Content-Type", s.contentType)
	_, _ = io.WriteString(w, s.body)
}

func envOf(values map[string]string) config.LookupFunc {
	return func(key string) (string, bool) {
		value, ok := values[key]
		return value, ok
	}
}

func serverEnv(url string, extra ...string) map[string]string {
	env := map[string]string{
		EnvURL:       url + "/",
		EnvProfile:   "docker",
		EnvUsername:  testUser,
		EnvPassword:  testPassword,
		EnvBackoffMS: fastBackoff,
	}
	for i := 0; i+1 < len(extra); i += 2 {
		env[extra[i]] = extra[i+1]
	}
	return env
}

func bufferLogger() (*slog.Logger, *bytes.Buffer) {
	var buf bytes.Buffer
	return slog.New(slog.NewJSONHandler(&buf, nil)), &buf
}

func discard() *slog.Logger {
	return slog.New(slog.DiscardHandler)
}

func TestResolveLoadsRemotePropertiesWithBasicAuth(t *testing.T) {
	server := newConfigServer(t, 0, 0)
	logger, logs := bufferLogger()
	lookup, err := Resolve(context.Background(), envOf(serverEnv(server.URL)), logger)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if got := server.path.Load(); got != "/products-api-docker.properties" {
		t.Fatalf("unexpected path %v", got)
	}
	if got := server.auth.Load(); got != testUser+":"+testPassword {
		t.Fatalf("basic auth not sent, got %v", got)
	}
	cfg, err := config.Load(lookup)
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.RateLimit.RPS != 50 || len(cfg.Faults.Rules) != 1 || cfg.Port != 8081 {
		t.Fatalf("remote values not applied or port leaked: %+v", cfg)
	}
	assertLoggedKeysOnly(t, logs.Bytes())
}

func assertLoggedKeysOnly(t *testing.T, logs []byte) {
	t.Helper()
	var entry struct {
		Keys  []string `json:"keys"`
		Count int      `json:"count"`
	}
	if err := json.Unmarshal(logs, &entry); err != nil {
		t.Fatalf("decode log: %v", err)
	}
	want := []string{"AUTH_CLIENT_SECRET", "AUTH_ISSUER", "AUTH_JWKS_URL", "FAULT_RULES",
		"RATE_LIMIT_RPS"}
	if entry.Count != len(want) || strings.Join(entry.Keys, ",") != strings.Join(want, ",") {
		t.Fatalf("want keys %v, got %+v", want, entry)
	}
	for _, value := range []string{testPassword, testSecret, "PRD-012", "keycloak:8080"} {
		if bytes.Contains(logs, []byte(value)) {
			t.Fatalf("value %q leaked into logs: %s", value, logs)
		}
	}
}

func TestResolvePrecedence(t *testing.T) {
	server := newConfigServer(t, 0, 0)
	env := serverEnv(server.URL, config.EnvRateLimitRPS, "7", config.EnvFaultRules, "")
	lookup, err := Resolve(context.Background(), envOf(env), discard())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	cfg, err := config.Load(lookup)
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.RateLimit.RPS != 7 || len(cfg.Faults.Rules) != 0 || cfg.RateLimit.Burst != 400 {
		t.Fatalf("precedence env (even empty) > remote > default broken: %+v", cfg)
	}
	if value, ok := lookup(config.EnvAuthIssuer); !ok || value == "" {
		t.Fatal("unset env must fall through to remote")
	}
	if _, ok := lookup("UNKNOWN_KEY"); ok {
		t.Fatal("unknown keys must be absent")
	}
}

func TestResolveRetriesThenSucceeds(t *testing.T) {
	server := newConfigServer(t, 2, http.StatusServiceUnavailable)
	lookup, err := Resolve(context.Background(), envOf(serverEnv(server.URL)), discard())
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if value, _ := lookup(config.EnvRateLimitRPS); value != "50" || server.calls.Load() != 3 {
		t.Fatalf("want success on third call, got %q after %d", value, server.calls.Load())
	}
}

func TestResolveUnavailable(t *testing.T) {
	cases := []struct {
		name        string
		status      int
		contentType string
		body        string
		wantCalls   int32
	}{
		{name: "should_retry_server_errors", status: 500, wantCalls: 4},
		{name: "should_retry_throttling", status: 429, wantCalls: 4},
		{name: "should_not_retry_unauthorized", status: 401, wantCalls: 1},
		{name: "should_not_retry_not_found", status: 404, wantCalls: 1},
		{name: "should_reject_json", contentType: "application/json", wantCalls: 1},
		{name: "should_reject_missing_type", contentType: ";", wantCalls: 1},
		{name: "should_reject_malformed_body", body: "key=\\uzz", wantCalls: 1},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			failures := int32(0)
			if tc.status != 0 {
				failures = 99
			}
			server := newConfigServer(t, failures, tc.status)
			server.contentType = cmpOr(tc.contentType, plainText)
			server.body = cmpOr(tc.body, remoteBody)
			env := serverEnv(server.URL, EnvFailFast, "true")
			_, err := Resolve(context.Background(), envOf(env), discard())
			if !errors.Is(err, ErrUnavailable) || server.calls.Load() != tc.wantCalls {
				t.Fatalf("want unavailable after %d calls, got %v after %d",
					tc.wantCalls, err, server.calls.Load())
			}
		})
	}
}

func cmpOr(value, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func TestResolveUnreachable(t *testing.T) {
	server := newConfigServer(t, 0, 0)
	url := server.URL
	server.Close()
	env := serverEnv(url, EnvRetries, "1", config.EnvPort, "8085")
	logger, logs := bufferLogger()
	lookup, err := Resolve(context.Background(), envOf(env), logger)
	if err != nil {
		t.Fatalf("want fallback without fail-fast, got %v", err)
	}
	if value, _ := lookup(config.EnvPort); value != "8085" {
		t.Fatalf("want env value on fallback, got %q", value)
	}
	if !strings.Contains(logs.String(), `"level":"WARN"`) ||
		strings.Contains(logs.String(), testPassword) {
		t.Fatalf("want warning without secrets, got %s", logs.String())
	}
	env[EnvFailFast] = "true"
	if _, err := Resolve(context.Background(), envOf(env), logger); !errors.Is(err, ErrUnavailable) {
		t.Fatalf("want unavailable with fail-fast, got %v", err)
	}
}

func TestResolveSkipsWithoutURL(t *testing.T) {
	logger, logs := bufferLogger()
	lookup, err := Resolve(context.Background(),
		envOf(map[string]string{config.EnvPort: "1234"}), logger)
	if err != nil || logs.Len() != 0 {
		t.Fatalf("want silent skip, got err=%v logs=%s", err, logs.String())
	}
	if value, _ := lookup(config.EnvPort); value != "1234" {
		t.Fatalf("want env passthrough, got %q", value)
	}
}

func TestResolveRejectsInvalidSettings(t *testing.T) {
	_, err := Resolve(context.Background(), envOf(map[string]string{EnvURL: "not-a-url"}),
		discard())
	if !errors.Is(err, config.ErrInvalid) {
		t.Fatalf("want ErrInvalid, got %v", err)
	}
}

func TestResolveHonoursCancellationDuringBackoff(t *testing.T) {
	server := newConfigServer(t, 99, http.StatusBadGateway)
	hour := strconv.Itoa(int(time.Hour / time.Millisecond))
	env := serverEnv(server.URL, EnvBackoffMS, hour, EnvBackoffMaxMS, hour, EnvFailFast, "true")
	ctx, cancel := context.WithCancel(context.Background())
	server.Config.Handler = http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		cancel()
		w.WriteHeader(http.StatusBadGateway)
	})
	_, err := Resolve(ctx, envOf(env), discard())
	if !errors.Is(err, context.Canceled) {
		t.Fatalf("want canceled, got %v", err)
	}
}

func TestLoadSettings(t *testing.T) {
	s, err := loadSettings(envOf(map[string]string{}))
	want := settings{appName: "products-api", profile: "default", timeout: 3 * time.Second,
		retries: 3, backoff: 200 * time.Millisecond, backoffMax: 2 * time.Second}
	if err != nil || s != want || s.enabled() {
		t.Fatalf("want %+v, got %+v err=%v", want, s, err)
	}
	s, err = loadSettings(envOf(map[string]string{EnvURL: "http://config:8888//",
		EnvRetries: "0", EnvFailFast: "true", EnvPassword: " raw "}))
	if err != nil || s.baseURL != "http://config:8888" || s.retries != 0 || !s.failFast ||
		s.password != " raw " || !s.enabled() {
		t.Fatalf("overrides not applied: %+v err=%v", s, err)
	}
}

func TestLoadSettingsRejectsInvalid(t *testing.T) {
	cases := map[string]string{
		EnvURL: "/relative", EnvTimeoutMS: "0", EnvRetries: "11",
		EnvBackoffMS: "abc", EnvBackoffMaxMS: "-5", EnvFailFast: "maybe",
	}
	for key, value := range cases {
		t.Run(key, func(t *testing.T) {
			_, err := loadSettings(envOf(map[string]string{key: value}))
			if !errors.Is(err, config.ErrInvalid) || !strings.Contains(err.Error(), key) {
				t.Fatalf("want invalid %s, got %v", key, err)
			}
		})
	}
}
