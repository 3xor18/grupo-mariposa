package app_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/app"
	"github.com/grupomariposa/platform/products-api/internal/auth/authtest"
	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/config/remote"
)

const (
	waitLimit    = 5 * time.Second
	pollInterval = 10 * time.Millisecond
	slowProduct  = "/products/PRD-013?market=MX"
	logListening = "http server listening"
	logDraining  = "draining before shutdown"
	logFault     = "fault injected"
)

type logWatcher struct {
	mu     sync.Mutex
	buf    bytes.Buffer
	notify chan struct{}
}

func newLogWatcher() *logWatcher {
	return &logWatcher{notify: make(chan struct{}, 1)}
}

func (l *logWatcher) Write(p []byte) (int, error) {
	l.mu.Lock()
	defer l.mu.Unlock()
	n, err := l.buf.Write(p)
	select {
	case l.notify <- struct{}{}:
	default:
	}
	return n, err
}

func (l *logWatcher) String() string {
	l.mu.Lock()
	defer l.mu.Unlock()
	return l.buf.String()
}

func (l *logWatcher) await(t *testing.T, message string) {
	t.Helper()
	deadline := time.After(waitLimit)
	for !strings.Contains(l.String(), `"message":"`+message+`"`) {
		select {
		case <-l.notify:
		case <-deadline:
			t.Fatalf("log %q not seen in time; logs:\n%s", message, l.String())
		}
	}
}

type instance struct {
	url    string
	port   string
	logs   *logWatcher
	cancel context.CancelFunc
	exit   chan int
}

func listenOn(t *testing.T) (net.Listener, app.ListenFunc) {
	t.Helper()
	var lc net.ListenConfig
	listener, err := lc.Listen(context.Background(), "tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	t.Cleanup(func() { _ = listener.Close() })
	return listener, func(context.Context, string, string) (net.Listener, error) {
		return listener, nil
	}
}

func baseEnv(extra ...string) map[string]string {
	env := map[string]string{
		config.EnvAuthEnabled:          "false",
		config.EnvShutdownDrainDelayMS: "0",
		config.EnvStorageDriver:        config.StorageMemory,
	}
	for i := 0; i+1 < len(extra); i += 2 {
		env[extra[i]] = extra[i+1]
	}
	return env
}

func lookup(values map[string]string) config.LookupFunc {
	return func(key string) (string, bool) {
		value, ok := values[key]
		return value, ok
	}
}

func start(t *testing.T, env map[string]string) *instance {
	t.Helper()
	listener, listen := listenOn(t)
	ctx, cancel := context.WithCancel(context.Background())
	_, port, _ := net.SplitHostPort(listener.Addr().String())
	inst := &instance{url: "http://" + listener.Addr().String(), port: port,
		logs: newLogWatcher(), cancel: cancel, exit: make(chan int, 1)}
	rt := app.Runtime{Env: lookup(env), Out: inst.logs, Listen: listen}
	go func() { inst.exit <- app.Main(ctx, nil, rt) }()
	t.Cleanup(func() {
		cancel()
		<-inst.exit
	})
	inst.logs.await(t, logListening)
	return inst
}

func (i *instance) stop(t *testing.T) int {
	t.Helper()
	i.cancel()
	select {
	case code := <-i.exit:
		i.exit <- code
		return code
	case <-time.After(waitLimit):
		t.Fatal("service did not stop in time")
		return app.ExitFailure
	}
}

func get(t *testing.T, url string, headers ...string) (int, string) {
	t.Helper()
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, url, nil)
	if err != nil {
		t.Errorf("request: %v", err)
		return 0, ""
	}
	for i := 0; i+1 < len(headers); i += 2 {
		req.Header.Set(headers[i], headers[i+1])
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return 0, err.Error()
	}
	defer func() { _ = resp.Body.Close() }()
	body, _ := io.ReadAll(resp.Body)
	return resp.StatusCode, string(body)
}

func healthStatus(t *testing.T, url string) string {
	t.Helper()
	_, body := get(t, url)
	var health struct {
		Status string `json:"status"`
	}
	_ = json.Unmarshal([]byte(body), &health)
	return health.Status
}

func TestServesProductsAndReadiness(t *testing.T) {
	inst := start(t, baseEnv())
	if code, _ := get(t, inst.url+"/products/PRD-001?market=CO"); code != http.StatusOK {
		t.Fatalf("want 200, got %d", code)
	}
	if status := healthStatus(t, inst.url+"/health/ready"); status != "UP" {
		t.Fatalf("want ready UP, got %q", status)
	}
	if code := inst.stop(t); code != app.ExitOK {
		t.Fatalf("want clean exit, got %d", code)
	}
}

func TestFaultInjectionIsGated(t *testing.T) {
	rules := []string{config.EnvFaultRules, "PRD-012:503"}
	disabled := start(t, baseEnv(rules...))
	if code, _ := get(t, disabled.url+"/products/PRD-012?market=MX"); code != http.StatusOK {
		t.Fatalf("faults must be off by default, got %d", code)
	}
	enabled := start(t, baseEnv(append(rules, config.EnvFaultInjectionEnabled, "true")...))
	if code, _ := get(t, enabled.url+"/products/PRD-012?market=MX"); code != 503 {
		t.Fatalf("enabled fault must apply, got %d", code)
	}
}

func TestGracefulShutdownDrainsWhileNotReady(t *testing.T) {
	inst := start(t, baseEnv(
		config.EnvFaultInjectionEnabled, "true", config.EnvFaultRules, "PRD-013:timeout",
		config.EnvFaultTimeoutMS, "300", config.EnvShutdownDrainDelayMS, "1000"))
	inFlight := make(chan int, 1)
	go func() {
		code, _ := get(t, inst.url+slowProduct)
		inFlight <- code
	}()
	inst.logs.await(t, logFault)
	inst.cancel()
	inst.logs.await(t, logDraining)
	if status := healthStatus(t, inst.url+"/health/ready"); status != "DOWN" {
		t.Fatalf("want readiness DOWN while draining, got %q", status)
	}
	if code := <-inFlight; code != http.StatusOK {
		t.Fatalf("in-flight request must complete, got %d", code)
	}
	if code := inst.stop(t); code != app.ExitOK {
		t.Fatalf("want clean exit, got %d", code)
	}
}

func TestShutdownTimeoutForcesClose(t *testing.T) {
	inst := start(t, baseEnv(
		config.EnvFaultInjectionEnabled, "true", config.EnvFaultRules, "PRD-013:timeout",
		config.EnvFaultTimeoutMS, "10000", config.EnvRequestTimeoutMS, "20000",
		config.EnvHTTPWriteTimeoutMS, "30000", config.EnvShutdownTimeoutMS, "1"))
	done := make(chan struct{})
	go func() {
		defer close(done)
		get(t, inst.url+slowProduct)
	}()
	inst.logs.await(t, logFault)
	if code := inst.stop(t); code != app.ExitFailure {
		t.Fatalf("want failure exit after forced close, got %d", code)
	}
	<-done
	if !strings.Contains(inst.logs.String(), "graceful shutdown") {
		t.Fatalf("want shutdown error logged, got %s", inst.logs.String())
	}
}

func TestReadinessWaitsForJWKSWarmUp(t *testing.T) {
	issuer := authtest.NewIssuer(t)
	issuer.SetAvailable(false)
	inst := start(t, baseEnv(config.EnvAuthEnabled, "true",
		config.EnvAuthIssuer, authtest.IssuerURL, config.EnvAuthJWKSURL, issuer.JWKSURL,
		config.EnvAuthAudience, authtest.Audience, config.EnvAuthJWKSMinRefreshMS, "10"))
	if status := healthStatus(t, inst.url+"/health/ready"); status != "DOWN" {
		t.Fatalf("want DOWN before jwks warm-up, got %q", status)
	}
	if status := healthStatus(t, inst.url+"/health/live"); status != "UP" {
		t.Fatalf("liveness must not depend on jwks, got %q", status)
	}
	token := issuer.Token(t, authtest.ValidClaims())
	if code, _ := get(t, inst.url+slowProduct, "Authorization", "Bearer "+token); code != 503 {
		t.Fatalf("want 503 while jwks is down, got %d", code)
	}
	issuer.SetAvailable(true)
	deadline := time.Now().Add(waitLimit)
	for healthStatus(t, inst.url+"/health/ready") != "UP" {
		if time.Now().After(deadline) {
			t.Fatal("readiness never came up after jwks recovered")
		}
		time.Sleep(pollInterval)
	}
	if code, _ := get(t, inst.url+slowProduct, "Authorization", "Bearer "+token); code != 200 {
		t.Fatalf("want 200 after warm-up, got %d", code)
	}
}

func TestMainLoadsConfigServerButKeepsPortFromEnvironment(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		_, _ = io.WriteString(w, "port: 1\nfault.injection-enabled: true\n"+
			"fault.rules: PRD-001:502\n")
	}))
	defer server.Close()
	inst := start(t, baseEnv(remote.EnvURL, server.URL))
	if code, _ := get(t, inst.url+"/products/PRD-001?market=MX"); code != 502 {
		t.Fatalf("remote fault rule must apply, got %d", code)
	}
	if !strings.Contains(inst.logs.String(), `"keys":["FAULT_INJECTION_ENABLED","FAULT_RULES"]`) {
		t.Fatalf("remote PORT must be ignored and keys logged, got %s", inst.logs.String())
	}
}

func TestHealthcheckModeOnlyNeedsPort(t *testing.T) {
	inst := start(t, baseEnv())
	env := map[string]string{
		config.EnvPort: inst.port, config.EnvAuthJWKSURL: "not-a-url",
		remote.EnvURL: "http://127.0.0.1:1", remote.EnvFailFast: "true",
	}
	rt := app.Runtime{Env: lookup(env), Out: io.Discard}
	if code := app.Main(context.Background(), []string{"-healthcheck"}, rt); code != app.ExitOK {
		t.Fatalf("want healthy probe, got %d", code)
	}
}

func TestHealthcheckModeFailures(t *testing.T) {
	unhealthy := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusServiceUnavailable)
	}))
	defer unhealthy.Close()
	_, port, _ := net.SplitHostPort(strings.TrimPrefix(unhealthy.URL, "http://"))
	for _, env := range []map[string]string{{config.EnvPort: port}, {config.EnvPort: "x"}} {
		rt := app.Runtime{Env: lookup(env), Out: io.Discard}
		if code := app.Main(context.Background(), []string{"-healthcheck"}, rt); code == 0 {
			t.Fatalf("env %v: want failure", env)
		}
	}
}

func TestMainFailures(t *testing.T) {
	closed, closedListen := listenOn(t)
	_ = closed.Close()
	failingListen := func(context.Context, string, string) (net.Listener, error) {
		return nil, errors.New("address in use")
	}
	cases := map[string]struct {
		args   []string
		env    map[string]string
		listen app.ListenFunc
	}{
		"unknown_flag":       {args: []string{"-nope"}, env: baseEnv()},
		"invalid_config":     {env: baseEnv(config.EnvPort, "x")},
		"listen_error":       {env: baseEnv(), listen: failingListen},
		"closed_listener":    {env: baseEnv(), listen: closedListen},
		"invalid_remote_url": {env: baseEnv(remote.EnvURL, "nope")},
		"mongo_uri_invalid": {env: baseEnv(config.EnvStorageDriver, config.StorageMongo,
			config.EnvMongoURI, "not-a-uri", config.EnvKafkaBootstrap, "127.0.0.1:1")},
		"mongo_unreachable": {env: baseEnv(config.EnvStorageDriver, config.StorageMongo,
			config.EnvMongoURI, "mongodb://127.0.0.1:1/?directConnection=true",
			config.EnvMongoTimeoutMS, "200", config.EnvKafkaBootstrap, "127.0.0.1:1")},
		"config_server_down": {env: baseEnv(remote.EnvURL, "http://127.0.0.1:1",
			remote.EnvRetries, "0", remote.EnvFailFast, "true")},
	}
	for name, tc := range cases {
		t.Run(name, func(t *testing.T) {
			rt := app.Runtime{Env: lookup(tc.env), Out: io.Discard, Listen: tc.listen}
			if code := app.Main(context.Background(), tc.args, rt); code != app.ExitFailure {
				t.Fatalf("want exit %d, got %d", app.ExitFailure, code)
			}
		})
	}
}

func TestDefaultListen(t *testing.T) {
	listener, err := app.DefaultListen(context.Background(), "tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("listen: %v", err)
	}
	defer func() { _ = listener.Close() }()
	if port := listener.Addr().(*net.TCPAddr).Port; port == 0 {
		t.Fatalf("want an assigned port, got %s", strconv.Itoa(port))
	}
}
