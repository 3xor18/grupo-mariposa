package telemetry

import (
	"net/http"
	"strconv"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/collectors"
	"github.com/prometheus/client_golang/prometheus/promhttp"
)

const (
	metricRequestsTotal   = "http_server_requests_total"
	metricRequestDuration = "http_server_request_duration_seconds"
	helpRequestsTotal     = "Total HTTP requests served."
	helpRequestDuration   = "HTTP request latency in seconds."
	labelMethod           = "method"
	labelRoute            = "route"
	labelStatus           = "status"
	metricOutboxPending   = "outbox_pending"
	metricOutboxPublished = "outbox_published_total"
	metricOutboxFailures  = "outbox_publish_failures_total"
	helpOutboxPending     = "Outbox events not yet published."
	helpOutboxPublished   = "Outbox events published to Kafka."
	helpOutboxFailures    = "Outbox events whose publication failed and will be retried."
)

type Metrics struct {
	registry  *prometheus.Registry
	requests  *prometheus.CounterVec
	duration  *prometheus.HistogramVec
	pending   prometheus.Gauge
	published prometheus.Counter
	failures  prometheus.Counter
}

func NewMetrics() *Metrics {
	labels := []string{labelMethod, labelRoute, labelStatus}
	m := &Metrics{
		registry: prometheus.NewRegistry(),
		requests: prometheus.NewCounterVec(
			prometheus.CounterOpts{Name: metricRequestsTotal, Help: helpRequestsTotal}, labels),
		duration: prometheus.NewHistogramVec(prometheus.HistogramOpts{
			Name:    metricRequestDuration,
			Help:    helpRequestDuration,
			Buckets: prometheus.DefBuckets,
		}, labels),
		pending: prometheus.NewGauge(prometheus.GaugeOpts{
			Name: metricOutboxPending, Help: helpOutboxPending}),
		published: prometheus.NewCounter(prometheus.CounterOpts{
			Name: metricOutboxPublished, Help: helpOutboxPublished}),
		failures: prometheus.NewCounter(prometheus.CounterOpts{
			Name: metricOutboxFailures, Help: helpOutboxFailures}),
	}
	m.registry.MustRegister(
		m.requests,
		m.duration,
		m.pending,
		m.published,
		m.failures,
		collectors.NewGoCollector(),
		collectors.NewProcessCollector(collectors.ProcessCollectorOpts{}),
	)
	return m
}

func (m *Metrics) ObserveRequest(method, route string, status int, elapsed time.Duration) {
	code := strconv.Itoa(status)
	m.requests.WithLabelValues(method, route, code).Inc()
	m.duration.WithLabelValues(method, route, code).Observe(elapsed.Seconds())
}

func (m *Metrics) OutboxPending(count int64) {
	m.pending.Set(float64(count))
}

func (m *Metrics) OutboxPublished(count int) {
	m.published.Add(float64(count))
}

func (m *Metrics) OutboxFailed(count int) {
	m.failures.Add(float64(count))
}

func (m *Metrics) Handler() http.Handler {
	return promhttp.HandlerFor(m.registry, promhttp.HandlerOpts{Registry: m.registry})
}
