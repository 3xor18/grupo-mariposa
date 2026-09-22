package remote

import (
	"context"
	"fmt"
	"log/slog"
	"net/url"
	"slices"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/config"
)

const (
	redacted          = "******"
	logLoaded         = "remote configuration loaded"
	logSkipped        = "config server unavailable, continuing with environment and defaults"
	logKeyURL         = "configServer"
	logKeyApplication = "application"
	logKeyProfile     = "profile"
	logKeyProperties  = "properties"
	logKeyError       = "error"
)

var sensitiveMarkers = []string{"SECRET", "PASSWORD", "KEY", "TOKEN"}

func Resolve(ctx context.Context, env config.LookupFunc, logger *slog.Logger) (
	config.LookupFunc, error,
) {
	settings, err := LoadSettings(env)
	if err != nil {
		return nil, err
	}
	if !settings.Enabled() {
		return env, nil
	}
	properties, err := NewClient(settings).Fetch(ctx)
	if err != nil {
		return failure(ctx, env, logger, settings, err)
	}
	remote := toEnvKeys(properties)
	logger.InfoContext(ctx, logLoaded, sourceAttrs(settings,
		slog.Any(logKeyProperties, Redact(remote)))...)
	return Layered(env, remote), nil
}

func failure(ctx context.Context, env config.LookupFunc, logger *slog.Logger, s Settings,
	err error,
) (config.LookupFunc, error) {
	if s.FailFast {
		return nil, fmt.Errorf("load remote configuration: %w", err)
	}
	logger.WarnContext(ctx, logSkipped, sourceAttrs(s, slog.String(logKeyError, err.Error()))...)
	return env, nil
}

func sourceAttrs(s Settings, extra slog.Attr) []any {
	return []any{
		slog.String(logKeyURL, redactedURL(s.BaseURL)),
		slog.String(logKeyApplication, s.AppName),
		slog.String(logKeyProfile, s.Profile),
		extra,
	}
}

func redactedURL(raw string) string {
	parsed, err := url.Parse(raw)
	if err != nil {
		return ""
	}
	return parsed.Redacted()
}

func Layered(env config.LookupFunc, remote map[string]string) config.LookupFunc {
	return func(key string) (string, bool) {
		if value, ok := env(key); ok && strings.TrimSpace(value) != "" {
			return value, true
		}
		value, ok := remote[key]
		return value, ok
	}
}

func Redact(properties map[string]string) map[string]string {
	safe := make(map[string]string, len(properties))
	for key, value := range properties {
		if IsSensitive(key) {
			value = redacted
		}
		safe[key] = value
	}
	return safe
}

func IsSensitive(key string) bool {
	upper := strings.ToUpper(key)
	return slices.ContainsFunc(sensitiveMarkers, func(marker string) bool {
		return strings.Contains(upper, marker)
	})
}

func toEnvKeys(properties map[string]string) map[string]string {
	converted := make(map[string]string, len(properties))
	for key, value := range properties {
		converted[EnvKey(key)] = value
	}
	return converted
}
