package httpapi

import (
	"log/slog"
	"net/http"
	"time"
)

const (
	RouteProduct = "GET /products/{productId}"
	RouteLive    = "GET /health/live"
	RouteReady   = "GET /health/ready"
	RouteMetrics = "GET /metrics"
	routeAny     = "/"
)

type Dependencies struct {
	Products       ProductFinder
	Verifier       TokenVerifier
	Faults         FaultInjector
	FaultHold      time.Duration
	RateLimit      RateLimit
	RequestTimeout time.Duration
	Readiness      Readiness
	Recorder       RequestRecorder
	MetricsHandler http.Handler
	Logger         *slog.Logger
	Clock          func() time.Time
}

func NewHandler(deps Dependencies) http.Handler {
	rs := responder{clock: deps.Clock, logger: deps.Logger}
	mux := http.NewServeMux()
	mux.Handle(RouteProduct, chain(productHandler{finder: deps.Products, responder: rs},
		productGuards(rs, deps)...))
	mux.HandleFunc(RouteLive, rs.live)
	mux.Handle(RouteReady, rs.ready(deps.Readiness))
	mux.Handle(RouteMetrics, deps.MetricsHandler)
	mux.HandleFunc(routeAny, rs.noRoute)
	return chain(mux, traceContext, rs.observe(deps.Recorder), rs.recoverPanic)
}

func productGuards(rs responder, deps Dependencies) []Middleware {
	guards := []Middleware{rs.rateLimit(deps.RateLimit)}
	if deps.Verifier != nil {
		guards = append(guards, rs.authenticate(deps.Verifier))
	}
	return append(guards, rs.injectFault(deps.Faults, deps.FaultHold), deadline(deps.RequestTimeout))
}
