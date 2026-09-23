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
	Products         ProductService
	ReaderRole       string
	AdminRole        string
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

type endpoint struct {
	method  string
	handler http.Handler
}

type route struct {
	path      string
	endpoints []endpoint
}

func NewHandler(deps Dependencies) http.Handler {
	rs := responder{clock: deps.Clock, logger: deps.Logger, typeBase: deps.ProblemTypeBase}
	mux := http.NewServeMux()
	for _, rt := range routes(rs, deps) {
		allowed := make([]string, 0, len(rt.endpoints)+1)
		for _, ep := range rt.endpoints {
			mux.Handle(ep.method+" "+rt.path, ep.handler)
			allowed = append(allowed, allowedMethods(ep.method)...)
		}
		mux.Handle(rt.path, rs.methodNotAllowed(allowed...))
	}
	mux.HandleFunc(pathAny, rs.noRoute)
	return chain(mux, traceContext, secureHeaders, rs.observe(deps.Recorder), rs.recoverPanic)
}

func allowedMethods(method string) []string {
	if method == http.MethodGet {
		return []string{http.MethodGet, http.MethodHead}
	}
	return []string{method}
}

func routes(rs responder, deps Dependencies) []route {
	products := productHandler{service: deps.Products, responder: rs}
	readGuards := append(productGuards(rs, deps, deps.ReaderRole), faultGuard(rs, deps)...)
	return []route{
		{path: pathProduct, endpoints: []endpoint{
			{method: http.MethodGet, handler: chain(http.HandlerFunc(products.get),
				readGuards...)},
			{method: http.MethodPatch, handler: chain(http.HandlerFunc(products.patch),
				productGuards(rs, deps, deps.AdminRole)...)},
		}},
		{path: pathLive, endpoints: getOnly(http.HandlerFunc(rs.live))},
		{path: pathReady, endpoints: getOnly(rs.ready(deps.Readiness))},
		{path: pathMetrics, endpoints: getOnly(deps.MetricsHandler)},
	}
}

func getOnly(handler http.Handler) []endpoint {
	return []endpoint{{method: http.MethodGet, handler: handler}}
}

func productGuards(rs responder, deps Dependencies, role string) []middleware {
	guards := []middleware{
		rs.limitBy(deps.ClientLimiter, clientAddress),
		deadline(deps.RequestTimeout),
	}
	if deps.Verifier != nil {
		guards = append(guards, rs.authenticate(deps.Verifier, role),
			rs.limitBy(deps.PrincipalLimiter, principalOf))
	}
	return guards
}

func faultGuard(rs responder, deps Dependencies) []middleware {
	if deps.Faults == nil {
		return nil
	}
	return []middleware{rs.injectFault(deps.Faults, deps.FaultHold)}
}
