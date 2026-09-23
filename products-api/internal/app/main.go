package app

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"net"
	"net/http"

	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/config/remote"
	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	ExitOK           = 0
	ExitFailure      = 1
	flagHealthcheck  = "healthcheck"
	usageHealthcheck = "probe GET /health/live on the local server and exit"
	liveURLFormat    = "http://127.0.0.1:%d/health/live"
	logInvalidConfig = "invalid configuration"
	logRunFailed     = "service failed"
	logHealthFailed  = "healthcheck failed"
	logKeyError      = "error"
)

var errUnhealthy = errors.New("unhealthy status")

type Runtime struct {
	Env    config.LookupFunc
	Out    io.Writer
	Listen ListenFunc
}

func DefaultListen(ctx context.Context, network, address string) (net.Listener, error) {
	var lc net.ListenConfig
	return lc.Listen(ctx, network, address)
}

func Main(ctx context.Context, args []string, rt Runtime) int {
	flags := flag.NewFlagSet(telemetry.ServiceName, flag.ContinueOnError)
	flags.SetOutput(rt.Out)
	healthcheck := flags.Bool(flagHealthcheck, false, usageHealthcheck)
	if err := flags.Parse(args); err != nil {
		return ExitFailure
	}
	bootstrap := telemetry.NewLogger(rt.Out, slog.LevelInfo)
	if *healthcheck {
		return report(ctx, bootstrap, probe(ctx, rt.Env), logHealthFailed)
	}
	lookup, err := remote.Resolve(ctx, rt.Env, bootstrap)
	if err != nil {
		return report(ctx, bootstrap, err, logInvalidConfig)
	}
	cfg, err := config.Load(lookup)
	if err != nil {
		return report(ctx, bootstrap, err, logInvalidConfig)
	}
	logger := telemetry.NewLogger(rt.Out, cfg.LogLevel)
	return report(ctx, logger, run(ctx, cfg, logger, rt.Listen), logRunFailed)
}

func report(ctx context.Context, logger *slog.Logger, err error, message string) int {
	if err == nil {
		return ExitOK
	}
	logger.ErrorContext(ctx, message, logKeyError, err.Error())
	return ExitFailure
}

func probe(ctx context.Context, env config.LookupFunc) error {
	settings, err := config.LoadProbe(env)
	if err != nil {
		return err
	}
	return checkHealth(ctx, fmt.Sprintf(liveURLFormat, settings.Port), settings)
}

func checkHealth(ctx context.Context, url string, settings config.Probe) error {
	ctx, cancel := context.WithTimeout(ctx, settings.Timeout)
	defer cancel()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return fmt.Errorf("build healthcheck request: %w", err)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return fmt.Errorf("healthcheck request: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("%w: %d", errUnhealthy, resp.StatusCode)
	}
	return nil
}
