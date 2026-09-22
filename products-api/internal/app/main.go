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

func Main(ctx context.Context, args []string, lookup config.LookupFunc, out io.Writer) int {
	flags := flag.NewFlagSet(telemetry.ServiceName, flag.ContinueOnError)
	flags.SetOutput(out)
	healthcheck := flags.Bool(flagHealthcheck, false, usageHealthcheck)
	if err := flags.Parse(args); err != nil {
		return ExitFailure
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
