package remote

import (
	"context"
	"fmt"
	"log/slog"
	"maps"
	"net/url"
	"slices"

	"github.com/grupomariposa/platform/products-api/internal/config"
)

const (
	logLoaded         = "remote configuration loaded"
	logSkipped        = "config server unavailable, continuing with environment and defaults"
	logKeyURL         = "configServer"
	logKeyApplication = "application"
	logKeyProfile     = "profile"
	logKeyKeys        = "keys"
	logKeyCount       = "count"
	logKeyError       = "error"
)

func Resolve(ctx context.Context, env config.LookupFunc, logger *slog.Logger) (
	config.LookupFunc, error,
) {
	s, err := loadSettings(env)
	if err != nil {
		return nil, fmt.Errorf("config server settings: %w", err)
	}
	if !s.enabled() {
		return env, nil
	}
	properties, err := newClient(s).fetch(ctx)
	if err != nil {
		return failure(ctx, env, logger, s, err)
	}
	remote := toEnvKeys(properties)
	keys := slices.Sorted(maps.Keys(remote))
	logger.InfoContext(ctx, logLoaded, sourceAttrs(s,
		slog.Any(logKeyKeys, keys), slog.Int(logKeyCount, len(keys)))...)
	return layered(env, remote), nil
}

func failure(ctx context.Context, env config.LookupFunc, logger *slog.Logger, s settings,
	err error,
) (config.LookupFunc, error) {
	if s.failFast {
		return nil, fmt.Errorf("load remote configuration: %w", err)
	}
	logger.WarnContext(ctx, logSkipped, sourceAttrs(s, slog.String(logKeyError, err.Error()))...)
	return env, nil
}

func sourceAttrs(s settings, extra ...any) []any {
	return append([]any{
		slog.String(logKeyURL, redactedURL(s.baseURL)),
		slog.String(logKeyApplication, s.appName),
		slog.String(logKeyProfile, s.profile),
	}, extra...)
}

func redactedURL(raw string) string {
	parsed, err := url.Parse(raw)
	if err != nil {
		return ""
	}
	return parsed.Redacted()
}

func layered(env config.LookupFunc, remote map[string]string) config.LookupFunc {
	return func(key string) (string, bool) {
		if value, ok := env(key); ok {
			return value, true
		}
		value, ok := remote[key]
		return value, ok
	}
}

func toEnvKeys(properties map[string]string) map[string]string {
	converted := make(map[string]string, len(properties))
	for property, value := range properties {
		if key := envKey(property); !config.EnvOnly(key) {
			converted[key] = value
		}
	}
	return converted
}
