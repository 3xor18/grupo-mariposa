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
	logKeyStorage       = "storage"
	logKeyMarkets       = "markets"
)

type ListenFunc func(ctx context.Context, network, address string) (net.Listener, error)

type application struct {
	cfg      config.Config
	logger   *slog.Logger
	handler  http.Handler
	verifier *auth.Verifier
	backend  backend
	serving  atomic.Bool
}

func newApplication(ctx context.Context, cfg config.Config, logger *slog.Logger,
) (*application, error) {
	metrics := telemetry.NewMetrics()
	store, err := openBackend(ctx, cfg, logger, metrics)
	if err != nil {
		return nil, err
	}
	a := &application{cfg: cfg, logger: logger, backend: store}
	deps := dependencies(cfg, logger, a, metrics)
	if cfg.Auth.Enabled {
		a.verifier = auth.NewVerifier(authSettings(cfg.Auth), logger)
		deps.Verifier = a.verifier
	}
	if cfg.Faults.Enabled {
		deps.Faults = fault.NewInjector(cfg.Faults.Rules)
	}
	a.handler = httpapi.NewHandler(deps)
	return a, nil
}

func dependencies(cfg config.Config, logger *slog.Logger, a *application,
	metrics *telemetry.Metrics,
) httpapi.Dependencies {
	limit := cfg.RateLimit
	events := catalog.NewChangeEvents(time.Now, newEventID)
	return httpapi.Dependencies{
		Products: catalog.NewService(a.backend.repository, a.backend.writer, cfg.Markets,
			events),
		ReaderRole:       cfg.Auth.RequiredRole,
		AdminRole:        cfg.Auth.AdminRole,
		FaultHold:        cfg.Faults.Timeout,
		ClientLimiter:    ratelimit.NewKeyed(limit.RPS, limit.Burst, limit.MaxKeys),
		PrincipalLimiter: ratelimit.NewKeyed(limit.RPS, limit.Burst, limit.MaxKeys),
		RequestTimeout:   cfg.RequestTimeout,
		Readiness:        a,
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
		ClockLeeway:    a.ClockLeeway,
		JWKSTimeout:    a.JWKSTimeout,
		Refresh:        a.JWKSRefresh,
		MinimumRefresh: a.JWKSMinimumRefresh,
	}
}

func (a *application) Ready(ctx context.Context) bool {
	if !a.serving.Load() || (a.verifier != nil && !a.verifier.Ready()) {
		return false
	}
	ctx, cancel := context.WithTimeout(ctx, a.cfg.Storage.Timeout)
	defer cancel()
	return a.backend.ping(ctx) == nil
}

func run(ctx context.Context, cfg config.Config, logger *slog.Logger, listen ListenFunc) error {
	application, err := newApplication(ctx, cfg, logger)
	if err != nil {
		return err
	}
	address := net.JoinHostPort("", strconv.Itoa(cfg.Port))
	listener, err := listen(ctx, network, address)
	if err != nil {
		return errors.Join(fmt.Errorf("listen on port %d: %w", cfg.Port, err),
			application.closeBackend(ctx))
	}
	return application.serve(ctx, listener)
}

func (a *application) serve(ctx context.Context, listener net.Listener) error {
	lifecycle, stopBackground := context.WithCancel(context.WithoutCancel(ctx))
	var background sync.WaitGroup
	a.startBackground(lifecycle, &background)
	err := a.serveUntilDone(ctx, listener)
	stopBackground()
	background.Wait()
	return errors.Join(err, a.closeBackend(ctx))
}

func (a *application) startBackground(ctx context.Context, background *sync.WaitGroup) {
	if a.verifier != nil {
		background.Go(func() { a.verifier.Run(ctx) })
	}
	if a.backend.relay != nil {
		background.Go(func() { a.backend.relay.Run(ctx) })
	}
}

func (a *application) serveUntilDone(ctx context.Context, listener net.Listener) error {
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
		logKeyFaultRules, len(a.cfg.Faults.Rules),
		logKeyStorage, a.cfg.Storage.Driver,
		logKeyMarkets, a.cfg.Markets.Codes())
}

func (a *application) closeBackend(ctx context.Context) error {
	closeCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), a.cfg.Storage.Timeout)
	defer cancel()
	return wrapClose(a.backend.close(closeCtx))
}

func wrapClose(err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("close backend: %w", err)
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
