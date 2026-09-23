package httpapi

import (
	"log/slog"
	"net/http"
	"time"
)

const (
	pathProduct = "/products/{productId}"
	pathLive    = "/health/live"
	pathReady   = "/health/ready"
	pathMetrics = "/metrics"
	pathAny     = "/"
)

type Dependencies struct {
	Products         ProductFinder
	Verifier         TokenVerifier
	Faults           FaultInjector
	FaultHold        time.Duration
	ClientLimiter    KeyedLimiter
	PrincipalLimiter KeyedLimiter
	RequestTimeout   time.Duration
	Readiness        Readiness
	Recorder         RequestRecorder
	MetricsHandler   http.Handler
	Logger           *slog.Logger
	Clock            func() time.Time
	ProblemTypeBase  string
}

type route struct {
	path    string
	handler http.Handler
}

func NewHandler(deps Dependencies) http.Handler {
	rs := responder{clock: deps.Clock, logger: deps.Logger, typeBase: deps.ProblemTypeBase}
	mux := http.NewServeMux()
	for _, rt := range routes(rs, deps) {
		mux.Handle(http.MethodGet+" "+rt.path, rt.handler)
		mux.Handle(rt.path, rs.methodNotAllowed(http.MethodGet, http.MethodHead))
	}
	mux.HandleFunc(pathAny, rs.noRoute)
	return chain(mux, traceContext, secureHeaders, rs.observe(deps.Recorder), rs.recoverPanic)
}

func routes(rs responder, deps Dependencies) []route {
	products := productHandler{finder: deps.Products, responder: rs}
	return []route{
		{path: pathProduct, handler: chain(products, productGuards(rs, deps)...)},
		{path: pathLive, handler: http.HandlerFunc(rs.live)},
		{path: pathReady, handler: rs.ready(deps.Readiness)},
		{path: pathMetrics, handler: deps.MetricsHandler},
	}
}

func productGuards(rs responder, deps Dependencies) []middleware {
	guards := []middleware{
		rs.limitBy(deps.ClientLimiter, clientAddress),
		deadline(deps.RequestTimeout),
	}
	if deps.Verifier != nil {
		guards = append(guards, rs.authenticate(deps.Verifier),
			rs.limitBy(deps.PrincipalLimiter, principalOf))
	}
	if deps.Faults != nil {
		guards = append(guards, rs.injectFault(deps.Faults, deps.FaultHold))
	}
	return guards
}
