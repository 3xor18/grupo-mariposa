package app

import (
	"context"
	"errors"
	"flag"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/config/remote"
	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	ExitOK             = 0
	ExitFailure        = 1
	flagHealthcheck    = "healthcheck"
	usageHealthcheck   = "probe GET /health/live on the local server and exit"
	liveURLFormat      = "http://127.0.0.1:%d/health/live"
	healthcheckTimeout = 2 * time.Second
	logInvalidConfig   = "invalid configuration"
	logRunFailed       = "service failed"
	logHealthFailed    = "healthcheck failed"
	logKeyError        = "error"
)

var errUnhealthy = errors.New("unhealthy status")

func Main(ctx context.Context, args []string, env config.LookupFunc, out io.Writer) int {
	flags := flag.NewFlagSet(telemetry.ServiceName, flag.ContinueOnError)
	flags.SetOutput(out)
	healthcheck := flags.Bool(flagHealthcheck, false, usageHealthcheck)
	if err := flags.Parse(args); err != nil {
		return ExitFailure
	}
	bootstrap := telemetry.NewLogger(out, slog.LevelInfo)
	lookup, err := resolveLookup(ctx, env, bootstrap, *healthcheck)
	if err != nil {
		return report(ctx, bootstrap, err, logInvalidConfig)
	}
	cfg, err := config.Load(lookup)
	logger := telemetry.NewLogger(out, cfg.LogLevel)
	slog.SetDefault(logger)
	if err != nil {
		return report(ctx, logger, err, logInvalidConfig)
	}
	if *healthcheck {
		err = CheckHealth(ctx, fmt.Sprintf(liveURLFormat, cfg.Port))
		return report(ctx, logger, err, logHealthFailed)
	}
	return report(ctx, logger, Run(ctx, cfg, logger), logRunFailed)
}

func resolveLookup(ctx context.Context, env config.LookupFunc, logger *slog.Logger,
	healthcheck bool,
) (config.LookupFunc, error) {
	if healthcheck {
		return env, nil
	}
	lookup, err := remote.Resolve(ctx, env, logger)
	if err != nil {
		return nil, fmt.Errorf("resolve configuration sources: %w", err)
	}
	return lookup, nil
}

func report(ctx context.Context, logger *slog.Logger, err error, message string) int {
	if err == nil {
		return ExitOK
	}
	logger.ErrorContext(ctx, message, logKeyError, err)
	return ExitFailure
}

func CheckHealth(ctx context.Context, url string) error {
	ctx, cancel := context.WithTimeout(ctx, healthcheckTimeout)
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
