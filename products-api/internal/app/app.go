package app

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"strconv"
	"sync/atomic"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/fault"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
	"github.com/grupomariposa/platform/products-api/internal/storage/memory"
	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	network           = "tcp"
	readHeaderTimeout = 5 * time.Second
	idleTimeout       = 60 * time.Second
	logListening      = "http server listening"
	logShuttingDown   = "shutting down"
	logStopped        = "http server stopped"
	logKeyAddress     = "address"
	logKeyAuthEnabled = "authEnabled"
	logKeyFaultRules  = "faultRules"
)

type App struct {
	cfg     config.Config
	logger  *slog.Logger
	handler http.Handler
	ready   atomic.Bool
}

func New(ctx context.Context, cfg config.Config, logger *slog.Logger) (*App, error) {
	verifier, err := newVerifier(ctx, cfg.Auth)
	if err != nil {
		return nil, err
	}
	metrics := telemetry.NewMetrics()
	a := &App{cfg: cfg, logger: logger}
	a.handler = httpapi.NewHandler(httpapi.Dependencies{
		Products:       catalog.NewService(memory.NewSeededRepository()),
		Verifier:       verifier,
		Faults:         fault.NewInjector(cfg.Faults.Rules),
		FaultHold:      cfg.Faults.Timeout,
		RateLimit:      httpapi.RateLimit{RPS: cfg.RateLimit.RPS, Burst: cfg.RateLimit.Burst},
		RequestTimeout: cfg.RequestTimeout,
		Readiness:      a,
		Recorder:       metrics,
		MetricsHandler: metrics.Handler(),
		Logger:         logger,
		Clock:          time.Now,
	})
	return a, nil
}

func newVerifier(ctx context.Context, settings config.Auth) (httpapi.TokenVerifier, error) {
	if !settings.Enabled {
		return nil, nil
	}
	verifier, err := auth.NewJWKSVerifier(ctx, auth.Settings{
		Issuer:       settings.Issuer,
		JWKSURL:      settings.JWKSURL,
		RequiredRole: settings.RequiredRole,
	})
	if err != nil {
		return nil, fmt.Errorf("configure authentication: %w", err)
	}
	return verifier, nil
}

func (a *App) Handler() http.Handler {
	return a.handler
}

func (a *App) Ready() bool {
	return a.ready.Load()
}

func Run(ctx context.Context, cfg config.Config, logger *slog.Logger) error {
	app, err := New(ctx, cfg, logger)
	if err != nil {
		return err
	}
	var lc net.ListenConfig
	listener, err := lc.Listen(ctx, network, net.JoinHostPort("", strconv.Itoa(cfg.Port)))
	if err != nil {
		return fmt.Errorf("listen on port %d: %w", cfg.Port, err)
	}
	return app.Serve(ctx, listener)
}

func (a *App) Serve(ctx context.Context, listener net.Listener) error {
	server := &http.Server{
		Handler:           a.handler,
		ReadHeaderTimeout: readHeaderTimeout,
		IdleTimeout:       idleTimeout,
	}
	served := make(chan error, 1)
	go func() { served <- server.Serve(listener) }()
	a.ready.Store(true)
	a.logger.InfoContext(ctx, logListening, logKeyAddress, listener.Addr().String(),
		logKeyAuthEnabled, a.cfg.Auth.Enabled, logKeyFaultRules, len(a.cfg.Faults.Rules))
	select {
	case err := <-served:
		a.ready.Store(false)
		return fmt.Errorf("serve http: %w", err)
	case <-ctx.Done():
	}
	return a.shutdown(ctx, server)
}

func (a *App) shutdown(ctx context.Context, server *http.Server) error {
	a.ready.Store(false)
	a.logger.InfoContext(ctx, logShuttingDown)
	shutdownCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), a.cfg.ShutdownTimeout)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		return errors.Join(fmt.Errorf("graceful shutdown: %w", err), server.Close())
	}
	a.logger.InfoContext(ctx, logStopped)
	return nil
}
