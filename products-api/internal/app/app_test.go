package app_test

import (
	"context"
	"errors"
	"io"
	"log/slog"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/app"
	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/config/remote"
	"github.com/grupomariposa/platform/products-api/internal/fault"
)

const (
	slowProduct  = "PRD-013"
	slowHold     = 300 * time.Millisecond
	waitDeadline = 5 * time.Second
	pollInterval = 10 * time.Millisecond
)

func testConfig() config.Config {
	return config.Config{
		RequestTimeout:  time.Second,
		ShutdownTimeout: waitDeadline,
		RateLimit:       config.RateLimit{RPS: 1000, Burst: 1000},
		Faults: config.Faults{
			Rules:   []fault.Rule{{ID: slowProduct, Kind: fault.KindTimeout}},
			Timeout: slowHold,
		},
	}
}

func discardLogger() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

type running struct {
	app    *app.App
	url    string
	cancel context.CancelFunc
	done   chan error
}

func start(t *testing.T, cfg config.Config) running {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	application, err := app.New(ctx, cfg, discardLogger())
	if err != nil {
		t.Fatalf("new app: %v", err)
	}
	listener := listen(t, "127.0.0.1:0")
	done := make(chan error, 1)
	go func() { done <- application.Serve(ctx, listener) }()
	r := running{app: application, url: "http://" + listener.Addr().String(), cancel: cancel,
		done: done}
	waitUntil(t, func() bool {
		return app.CheckHealth(context.Background(), r.url+"/health/live") == nil
	})
	return r
}

func waitUntil(t *testing.T, condition func() bool) {
	t.Helper()
	deadline := time.Now().Add(waitDeadline)
	for !condition() {
		if time.Now().After(deadline) {
			t.Fatal("condition not met in time")
		}
		time.Sleep(pollInterval)
	}
}

func TestServeReportsReadinessAndServesProducts(t *testing.T) {
	r := start(t, testConfig())
	defer r.cancel()
	if !r.app.Ready() {
		t.Fatal("app must be ready while serving")
	}
	if code := status(t, r.url+"/products/PRD-001?market=CO"); code != http.StatusOK {
		t.Fatalf("want 200, got %d", code)
	}
	if err := app.CheckHealth(context.Background(), r.url+"/health/ready"); err != nil {
		t.Fatalf("want ready, got %v", err)
	}
}

func TestGracefulShutdownDrainsInFlightRequests(t *testing.T) {
	r := start(t, testConfig())
	inFlight := make(chan int, 1)
	go func() {
		inFlight <- status(t, r.url+"/products/"+slowProduct+"?market=MX")
	}()
	time.Sleep(slowHold / 3)
	r.cancel()
	if err := <-r.done; err != nil {
		t.Fatalf("want clean shutdown, got %v", err)
	}
	if status := <-inFlight; status != http.StatusOK {
		t.Fatalf("in-flight request must complete, got %d", status)
	}
	if r.app.Ready() {
		t.Fatal("app must not be ready after shutdown")
	}
}

func TestShutdownTimeoutForcesClose(t *testing.T) {
	cfg := testConfig()
	cfg.ShutdownTimeout = time.Millisecond
	cfg.Faults.Timeout = waitDeadline
	r := start(t, cfg)
	go fireAndForget(r.url + "/products/" + slowProduct + "?market=MX")
	time.Sleep(slowHold / 3)
	r.cancel()
	if err := <-r.done; !errors.Is(err, context.DeadlineExceeded) {
		t.Fatalf("want deadline exceeded, got %v", err)
	}
}

func TestServeFailsWhenListenerClosed(t *testing.T) {
	application, err := app.New(context.Background(), testConfig(), discardLogger())
	if err != nil {
		t.Fatalf("new app: %v", err)
	}
	listener := listen(t, "127.0.0.1:0")
	_ = listener.Close()
	if err := application.Serve(context.Background(), listener); err == nil {
		t.Fatal("want serve error")
	}
	if application.Ready() || application.Handler() == nil {
		t.Fatal("unexpected state after failure")
	}
}

func TestNewWithAuthentication(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	cfg := testConfig()
	cfg.Auth = config.Auth{Enabled: true, Issuer: authtest.IssuerURL, JWKSURL: issuer.JWKSURL,
		RequiredRole: authtest.RequiredRole}
	r := start(t, cfg)
	defer r.cancel()
	if code := status(t, r.url+"/products/PRD-001?market=MX"); code != http.StatusUnauthorized {
		t.Fatalf("want 401 without token, got %d", code)
	}
	cfg.Auth.JWKSURL = "://bad"
	if _, err := app.New(context.Background(), cfg, discardLogger()); err == nil {
		t.Fatal("want error for invalid jwks url")
	}
}

func TestRunFailsWhenPortBusy(t *testing.T) {
	listener := listen(t, ":0")
	defer func() { _ = listener.Close() }()
	cfg := testConfig()
	cfg.Port = listener.Addr().(*net.TCPAddr).Port
	if err := app.Run(context.Background(), cfg, discardLogger()); err == nil {
		t.Fatal("want listen error")
	}
	cfg.Auth = config.Auth{Enabled: true, JWKSURL: "://bad"}
	if err := app.Run(context.Background(), cfg, discardLogger()); err == nil {
		t.Fatal("want configuration error")
	}
}

func TestCheckHealth(t *testing.T) {
	unhealthy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer unhealthy.Close()
	for _, url := range []string{unhealthy.URL, "http://127.0.0.1:1/health/live", "::bad"} {
		if err := app.CheckHealth(context.Background(), url); err == nil {
			t.Fatalf("%s: want error", url)
		}
	}
}

func status(t *testing.T, url string) int {
	t.Helper()
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, url, nil)
	if err != nil {
		t.Errorf("request: %v", err)
		return 0
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Errorf("get %s: %v", url, err)
		return 0
	}
	defer func() { _ = resp.Body.Close() }()
	return resp.StatusCode
}

func fireAndForget(url string) {
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, url, nil)
	if err != nil {
		return
	}
	if resp, err := http.DefaultClient.Do(req); err == nil {
		_ = resp.Body.Close()
	}
}

func listen(t *testing.T, address string) net.Listener {
	t.Helper()
	var lc net.ListenConfig
	listener, err := lc.Listen(context.Background(), "tcp", address)
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	return listener
}

func freePort(t *testing.T) int {
	t.Helper()
	listener := listen(t, "127.0.0.1:0")
	port := listener.Addr().(*net.TCPAddr).Port
	_ = listener.Close()
	return port
}

func env(values map[string]string) config.LookupFunc {
	return func(key string) (string, bool) {
		value, ok := values[key]
		return value, ok
	}
}

func baseEnv(port int) map[string]string {
	return map[string]string{
		config.EnvPort:        strconv.Itoa(port),
		config.EnvAuthEnabled: "false",
	}
}

func withEnv(env map[string]string, pairs ...string) map[string]string {
	for i := 0; i+1 < len(pairs); i += 2 {
		env[pairs[i]] = pairs[i+1]
	}
	return env
}

func TestMainLoadsConfigServer(t *testing.T) {
	port := freePort(t)
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = io.WriteString(w, "port: "+strconv.Itoa(port)+"\nauth.enabled: false\n")
	}))
	defer server.Close()
	remoteEnv := env(map[string]string{remote.EnvURL: server.URL})
	ctx, cancel := context.WithCancel(context.Background())
	exit := make(chan int, 1)
	go func() { exit <- app.Main(ctx, nil, remoteEnv, io.Discard) }()
	liveURL := "http://127.0.0.1:" + strconv.Itoa(port) + "/health/live"
	waitUntil(t, func() bool { return app.CheckHealth(context.Background(), liveURL) == nil })
	cancel()
	if code := <-exit; code != app.ExitOK {
		t.Fatalf("want exit 0, got %d", code)
	}
}

func TestHealthcheckIgnoresFullConfiguration(t *testing.T) {
	r := start(t, testConfig())
	defer r.cancel()
	_, port, err := net.SplitHostPort(strings.TrimPrefix(r.url, "http://"))
	if err != nil {
		t.Fatalf("split address: %v", err)
	}
	probeEnv := env(map[string]string{
		config.EnvPort: port, config.EnvAuthJWKSURL: "not-a-url",
		remote.EnvURL: "http://127.0.0.1:1", remote.EnvFailFast: "true",
	})
	code := app.Main(context.Background(), []string{"-healthcheck"}, probeEnv, io.Discard)
	if code != app.ExitOK {
		t.Fatalf("want healthy probe without auth settings, got exit %d", code)
	}
	invalidPort := env(map[string]string{config.EnvPort: "x"})
	code = app.Main(context.Background(), []string{"-healthcheck"}, invalidPort, io.Discard)
	if code != app.ExitFailure {
		t.Fatalf("want failure for invalid port, got %d", code)
	}
}

func TestMainRunsAndHealthchecks(t *testing.T) {
	port := freePort(t)
	lookup := env(baseEnv(port))
	ctx, cancel := context.WithCancel(context.Background())
	exit := make(chan int, 1)
	go func() { exit <- app.Main(ctx, nil, lookup, io.Discard) }()
	waitUntil(t, func() bool {
		return app.Main(context.Background(), []string{"-healthcheck"}, lookup, io.Discard) == 0
	})
	cancel()
	if code := <-exit; code != app.ExitOK {
		t.Fatalf("want exit 0, got %d", code)
	}
}

func TestMainFailures(t *testing.T) {
	busy := listen(t, ":0")
	defer func() { _ = busy.Close() }()
	cases := map[string]struct {
		args []string
		env  map[string]string
	}{
		"unknown_flag":       {args: []string{"-nope"}, env: baseEnv(freePort(t))},
		"invalid_config":     {env: map[string]string{config.EnvPort: "x"}},
		"healthcheck_down":   {args: []string{"-healthcheck"}, env: baseEnv(freePort(t))},
		"port_already_taken": {env: baseEnv(busy.Addr().(*net.TCPAddr).Port)},
		"config_server_down": {env: withEnv(baseEnv(freePort(t)),
			remote.EnvURL, "http://127.0.0.1:1", remote.EnvRetries, "0",
			remote.EnvFailFast, "true")},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			code := app.Main(context.Background(), tc.args, env(tc.env), io.Discard)
			if code != app.ExitFailure {
				t.Fatalf("want exit %d, got %d", app.ExitFailure, code)
			}
		})
	}
}
