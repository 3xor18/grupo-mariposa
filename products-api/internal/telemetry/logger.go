package telemetry

import (
	"context"
	"io"
	"log/slog"
)

const (
	ServiceName  = "products-api"
	keyService   = "service"
	keyTraceID   = "traceId"
	keyTimestamp = "timestamp"
	keyMessage   = "message"
)

type traceKey struct{}

func WithTraceID(ctx context.Context, traceID string) context.Context {
	return context.WithValue(ctx, traceKey{}, traceID)
}

func TraceID(ctx context.Context) string {
	traceID, _ := ctx.Value(traceKey{}).(string)
	return traceID
}

func NewLogger(w io.Writer, level slog.Level) *slog.Logger {
	json := slog.NewJSONHandler(w, &slog.HandlerOptions{Level: level, ReplaceAttr: renameKeys})
	return slog.New(traceHandler{Handler: json}).With(keyService, ServiceName)
}

func renameKeys(groups []string, attr slog.Attr) slog.Attr {
	if len(groups) > 0 {
		return attr
	}
	switch attr.Key {
	case slog.TimeKey:
		attr.Key = keyTimestamp
	case slog.MessageKey:
		attr.Key = keyMessage
	}
	return attr
}

type traceHandler struct {
	slog.Handler
}

func (h traceHandler) Handle(ctx context.Context, record slog.Record) error {
	if traceID := TraceID(ctx); traceID != "" {
		record.AddAttrs(slog.String(keyTraceID, traceID))
	}
	return h.Handler.Handle(ctx, record)
}

func (h traceHandler) WithAttrs(attrs []slog.Attr) slog.Handler {
	return traceHandler{Handler: h.Handler.WithAttrs(attrs)}
}

func (h traceHandler) WithGroup(name string) slog.Handler {
	return traceHandler{Handler: h.Handler.WithGroup(name)}
}
