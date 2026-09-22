package remote_test

import (
	"bytes"
	"context"
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
	"github.com/grupomariposa/platform/products-api/internal/config/remote"
)

const (
	testUser     = "config-user"
	testPassword = "s3cr3t-pass"
	testSecret   = "super-secret-token-value"
	fastBackoff  = "1"
	remoteBody   = "rate-limit.rps: 50\n" +
		"fault.rules: PRD-012:503:2\n" +
		"auth.jwks-url: http://keycloak:8080/realms/mariposa/protocol/openid-connect/certs\n" +
		"auth.client-secret: " + testSecret + "\n" +
		"auth.issuer: http://localhost:8180/realms/mariposa\n" +
		"port: 9999\n"
)

type configServer struct {
	*httptest.Server
	calls    atomic.Int32
	failures int32
	status   int
	path     atomic.Value
	auth     atomic.Value
}

func newConfigServer(t *testing.T, failures int32, status int) *configServer {
	t.Helper()
	s := &configServer{failures: failures, status: status}
	s.Server = httptest.NewServer(http.HandlerFunc(s.serve))
	t.Cleanup(s.Close)
	return s
}

func (s *configServer) serve(w http.ResponseWriter, r *http.Request) {
	s.path.Store(r.URL.Path)
	user, pass, _ := r.BasicAuth()
	s.auth.Store(user + ":" + pass)
	if s.calls.Add(1) <= s.failures {
		w.WriteHeader(s.status)
		return
	}
	w.Header().Set("Content-Type", "text/plain")
	_, _ = io.WriteString(w, remoteBody)
}

func envOf(values map[string]string) config.LookupFunc {
	return func(key string) (string, bool) {
		value, ok := values[key]
		return value, ok
	}
}

func serverEnv(url string, extra ...string) map[string]string {
	env := map[string]string{
		remote.EnvURL:       url + "/",
		remote.EnvProfile:   "docker",
		remote.EnvUsername:  testUser,
		remote.EnvPassword:  testPassword,
		remote.EnvBackoffMS: fastBackoff,
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

func TestResolveLoadsRemotePropertiesWithBasicAuth(t *testing.T) {
	server := newConfigServer(t, 0, 0)
	logger, logs := bufferLogger()
	lookup, err := remote.Resolve(context.Background(), envOf(serverEnv(server.URL)), logger)
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
	if cfg.RateLimit.RPS != 50 || cfg.Port != 9999 || len(cfg.Faults.Rules) != 1 {
		t.Fatalf("remote values not applied: %+v", cfg)
	}
	assertNoSecrets(t, logs.String())
}

func TestResolveEnvironmentWinsOverRemote(t *testing.T) {
	server := newConfigServer(t, 0, 0)
	env := serverEnv(server.URL, config.EnvRateLimitRPS, "7", config.EnvPort, " ")
	lookup, err := remote.Resolve(context.Background(), envOf(env), slog.New(slog.DiscardHandler))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	cfg, err := config.Load(lookup)
	if err != nil {
		t.Fatalf("load config: %v", err)
	}
	if cfg.RateLimit.RPS != 7 || cfg.Port != 9999 || cfg.RateLimit.Burst != 400 {
		t.Fatalf("precedence env > remote > default broken: %+v", cfg.RateLimit)
	}
	if _, ok := lookup("UNKNOWN_KEY"); ok {
		t.Fatal("unknown keys must be absent")
	}
}

func TestResolveRetriesThenSucceeds(t *testing.T) {
	server := newConfigServer(t, 2, http.StatusServiceUnavailable)
	lookup, err := remote.Resolve(context.Background(), envOf(serverEnv(server.URL)),
		slog.New(slog.DiscardHandler))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if value, _ := lookup(config.EnvRateLimitRPS); value != "50" || server.calls.Load() != 3 {
		t.Fatalf("want success on third call, got %q after %d", value, server.calls.Load())
	}
}

func TestResolveUnavailable(t *testing.T) {
	cases := []struct {
		name     string
		failures int32
		status   int
		wantCall int32
	}{
		{name: "should_retry_server_errors", failures: 99, status: 500, wantCall: 4},
		{name: "should_retry_throttling", failures: 99, status: 429, wantCall: 4},
		{name: "should_not_retry_unauthorized", failures: 99, status: 401, wantCall: 1},
		{name: "should_not_retry_not_found", failures: 99, status: 404, wantCall: 1},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			server := newConfigServer(t, tc.failures, tc.status)
			env := serverEnv(server.URL, remote.EnvFailFast, "true")
			_, err := remote.Resolve(context.Background(), envOf(env), slog.New(slog.DiscardHandler))
			if !errors.Is(err, remote.ErrUnavailable) || server.calls.Load() != tc.wantCall {
				t.Fatalf("want unavailable after %d calls, got %v after %d",
					tc.wantCall, err, server.calls.Load())
			}
		})
	}
}

func TestResolveUnreachable(t *testing.T) {
	server := newConfigServer(t, 0, 0)
	url := server.URL
	server.Close()
	env := serverEnv(url, remote.EnvRetries, "1", config.EnvPort, "8085")
	logger, logs := bufferLogger()
	lookup, err := remote.Resolve(context.Background(), envOf(env), logger)
	if err != nil {
		t.Fatalf("want fallback without fail-fast, got %v", err)
	}
	if value, _ := lookup(config.EnvPort); value != "8085" {
		t.Fatalf("want env value on fallback, got %q", value)
	}
	if !strings.Contains(logs.String(), `"level":"WARN"`) {
		t.Fatalf("want warning log, got %s", logs.String())
	}
	assertNoSecrets(t, logs.String())
	env[remote.EnvFailFast] = "true"
	if _, err := remote.Resolve(context.Background(), envOf(env), logger); err == nil {
		t.Fatal("want error with fail-fast")
	}
}

func TestResolveSkipsWithoutURL(t *testing.T) {
	logger, logs := bufferLogger()
	lookup, err := remote.Resolve(context.Background(),
		envOf(map[string]string{config.EnvPort: "1234"}), logger)
	if err != nil || logs.Len() != 0 {
		t.Fatalf("want silent skip, got err=%v logs=%s", err, logs.String())
	}
	if value, _ := lookup(config.EnvPort); value != "1234" {
		t.Fatalf("want env passthrough, got %q", value)
	}
}

func TestResolveRejectsInvalidSettings(t *testing.T) {
	_, err := remote.Resolve(context.Background(),
		envOf(map[string]string{remote.EnvURL: "not-a-url"}), slog.New(slog.DiscardHandler))
	if !errors.Is(err, remote.ErrInvalidSettings) {
		t.Fatalf("want ErrInvalidSettings, got %v", err)
	}
}

func TestResolveHonoursCancellationDuringBackoff(t *testing.T) {
	server := newConfigServer(t, 99, http.StatusBadGateway)
	env := serverEnv(server.URL, remote.EnvBackoffMS, strconv.Itoa(int(time.Hour/time.Millisecond)),
		remote.EnvFailFast, "true")
	ctx, cancel := context.WithTimeout(context.Background(), 50*time.Millisecond)
	defer cancel()
	_, err := remote.Resolve(ctx, envOf(env), slog.New(slog.DiscardHandler))
	if !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("want deadline exceeded, got %v", err)
	}
}

func TestFetchRejectsMalformedBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = io.WriteString(w, "key=\\uzz")
	}))
	defer server.Close()
	client := remote.NewClient(remote.Settings{BaseURL: server.URL, AppName: "a", Profile: "p",
		Timeout: time.Second, Retries: 3, Backoff: time.Millisecond})
	if _, err := client.Fetch(context.Background()); !errors.Is(err, remote.ErrUnavailable) {
		t.Fatalf("want unavailable, got %v", err)
	}
}

func TestFetchRejectsUnbuildableURL(t *testing.T) {
	client := remote.NewClient(remote.Settings{BaseURL: "http://host", AppName: "%zz",
		Timeout: time.Second, Backoff: time.Millisecond})
	if _, err := client.Fetch(context.Background()); err == nil {
		t.Fatal("want request build error")
	}
}

func assertNoSecrets(t *testing.T, logs string) {
	t.Helper()
	for _, secret := range []string{testPassword, testSecret} {
		if strings.Contains(logs, secret) {
			t.Fatalf("secret %q leaked into logs: %s", secret, logs)
		}
	}
}
