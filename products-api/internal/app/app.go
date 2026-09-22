package app

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/auth"
	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/fault"
	"github.com/grupomariposa/platform/products-api/internal/httpapi"
	"github.com/grupomariposa/platform/products-api/internal/ratelimit"
	"github.com/grupomariposa/platform/products-api/internal/storage/memory"
	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const (
	network             = "tcp"
	logListening        = "http server listening"
	logDraining         = "draining before shutdown"
	logStopped          = "http server stopped"
	logKeyAddress       = "address"
	logKeyAuthEnabled   = "authEnabled"
	logKeyFaultsEnabled = "faultInjectionEnabled"
	logKeyFaultRules    = "faultRules"
	logKeyDrainDelay    = "drainDelayMs"
)

type ListenFunc func(ctx context.Context, network, address string) (net.Listener, error)

type application struct {
	cfg      config.Config
	logger   *slog.Logger
	handler  http.Handler
	verifier *auth.Verifier
	serving  atomic.Bool
}

func newApplication(cfg config.Config, logger *slog.Logger) *application {
	a := &application{cfg: cfg, logger: logger}
	deps := dependencies(cfg, logger, a)
	if cfg.Auth.Enabled {
		a.verifier = auth.NewVerifier(authSettings(cfg.Auth), logger)
		deps.Verifier = a.verifier
	}
	if cfg.Faults.Enabled {
		deps.Faults = fault.NewInjector(cfg.Faults.Rules)
	}
	a.handler = httpapi.NewHandler(deps)
	return a
}

func dependencies(cfg config.Config, logger *slog.Logger, readiness httpapi.Readiness,
) httpapi.Dependencies {
	metrics := telemetry.NewMetrics()
	limit := cfg.RateLimit
	return httpapi.Dependencies{
		Products:         catalog.NewService(memory.NewSeededRepository()),
		FaultHold:        cfg.Faults.Timeout,
		ClientLimiter:    ratelimit.NewKeyed(limit.RPS, limit.Burst, limit.MaxKeys),
		PrincipalLimiter: ratelimit.NewKeyed(limit.RPS, limit.Burst, limit.MaxKeys),
		RequestTimeout:   cfg.RequestTimeout,
		Readiness:        readiness,
		Recorder:         metrics,
		MetricsHandler:   metrics.Handler(),
		Logger:           logger,
		Clock:            time.Now,
		ProblemTypeBase:  cfg.ProblemTypeBase,
	}
}

func authSettings(a config.Auth) auth.Settings {
	return auth.Settings{
		Issuer:         a.Issuer,
		Audience:       a.Audience,
		JWKSURL:        a.JWKSURL,
		RequiredRole:   a.RequiredRole,
		ClockLeeway:    a.ClockLeeway,
		JWKSTimeout:    a.JWKSTimeout,
		Refresh:        a.JWKSRefresh,
		MinimumRefresh: a.JWKSMinimumRefresh,
	}
}

func (a *application) Ready() bool {
	return a.serving.Load() && (a.verifier == nil || a.verifier.Ready())
}

func run(ctx context.Context, cfg config.Config, logger *slog.Logger, listen ListenFunc) error {
	address := net.JoinHostPort("", strconv.Itoa(cfg.Port))
	listener, err := listen(ctx, network, address)
	if err != nil {
		return fmt.Errorf("listen on port %d: %w", cfg.Port, err)
	}
	return newApplication(cfg, logger).serve(ctx, listener)
}

func (a *application) serve(ctx context.Context, listener net.Listener) error {
	lifecycle, stopBackground := context.WithCancel(context.WithoutCancel(ctx))
	var background sync.WaitGroup
	defer func() {
		stopBackground()
		background.Wait()
	}()
	if a.verifier != nil {
		background.Go(func() { a.verifier.Run(lifecycle) })
	}
	server := a.newServer()
	served := make(chan error, 1)
	go func() { served <- server.Serve(listener) }()
	a.serving.Store(true)
	a.logStart(ctx, listener)
	select {
	case err := <-served:
		a.serving.Store(false)
		return fmt.Errorf("serve http: %w", err)
	case <-ctx.Done():
	}
	return a.shutdown(ctx, server)
}

func (a *application) newServer() *http.Server {
	h := a.cfg.HTTP
	return &http.Server{
		Handler:           a.handler,
		ReadHeaderTimeout: h.ReadHeaderTimeout,
		ReadTimeout:       h.ReadTimeout,
		WriteTimeout:      h.WriteTimeout,
		IdleTimeout:       h.IdleTimeout,
		MaxHeaderBytes:    h.MaxHeaderBytes,
		ErrorLog:          slog.NewLogLogger(a.logger.Handler(), slog.LevelWarn),
	}
}

func (a *application) logStart(ctx context.Context, listener net.Listener) {
	a.logger.InfoContext(ctx, logListening, logKeyAddress, listener.Addr().String(),
		logKeyAuthEnabled, a.cfg.Auth.Enabled,
		logKeyFaultsEnabled, a.cfg.Faults.Enabled,
		logKeyFaultRules, len(a.cfg.Faults.Rules))
}

func (a *application) shutdown(ctx context.Context, server *http.Server) error {
	a.serving.Store(false)
	drain := a.cfg.Shutdown.DrainDelay
	a.logger.InfoContext(ctx, logDraining, logKeyDrainDelay, drain.Milliseconds())
	time.Sleep(drain)
	shutdownCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), a.cfg.Shutdown.Timeout)
	defer cancel()
	if err := server.Shutdown(shutdownCtx); err != nil {
		return errors.Join(fmt.Errorf("graceful shutdown: %w", err), server.Close())
	}
	a.logger.InfoContext(ctx, logStopped)
	return nil
}
