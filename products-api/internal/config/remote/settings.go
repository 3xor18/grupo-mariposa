package remote

import (
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/config"
)

const (
	EnvURL       = "CONFIG_SERVER_URL"
	EnvAppName   = "CONFIG_APP_NAME"
	EnvProfile   = "CONFIG_PROFILE"
	EnvUsername  = "CONFIG_SERVER_USERNAME"
	EnvPassword  = "CONFIG_SERVER_PASSWORD"
	EnvTimeoutMS = "CONFIG_SERVER_TIMEOUT_MS"
	EnvRetries   = "CONFIG_SERVER_RETRIES"
	EnvBackoffMS = "CONFIG_SERVER_BACKOFF_MS"
	EnvFailFast  = "CONFIG_SERVER_FAIL_FAST"

	defaultAppName   = "products-api"
	defaultProfile   = "default"
	defaultTimeoutMS = 3000
	defaultRetries   = 3
	defaultBackoffMS = 200
	urlPathSeparator = "/"
)

var (
	ErrInvalidSettings = errors.New("invalid config server settings")
	errNegative        = errors.New("must not be negative")
	errNotPositive     = errors.New("must be positive")
	errNotAbsoluteURL  = errors.New("must be an absolute URL")
)

type Settings struct {
	BaseURL  string
	AppName  string
	Profile  string
	Username string
	Password string
	Timeout  time.Duration
	Retries  int
	Backoff  time.Duration
	FailFast bool
}

func (s Settings) Enabled() bool {
	return s.BaseURL != ""
}

func LoadSettings(lookup config.LookupFunc) (Settings, error) {
	r := settingsReader{lookup: lookup}
	s := Settings{
		BaseURL:  strings.TrimRight(r.text(EnvURL, ""), urlPathSeparator),
		AppName:  r.text(EnvAppName, defaultAppName),
		Profile:  r.text(EnvProfile, defaultProfile),
		Username: r.text(EnvUsername, ""),
		Password: r.raw(EnvPassword),
		Timeout:  r.millis(EnvTimeoutMS, defaultTimeoutMS),
		Retries:  r.count(EnvRetries, defaultRetries),
		Backoff:  r.millis(EnvBackoffMS, defaultBackoffMS),
		FailFast: r.boolean(EnvFailFast),
	}
	r.absoluteURL(EnvURL, s.BaseURL)
	if err := errors.Join(r.errs...); err != nil {
		return Settings{}, fmt.Errorf("%w: %w", ErrInvalidSettings, err)
	}
	return s, nil
}

type settingsReader struct {
	lookup config.LookupFunc
	errs   []error
}

func (r *settingsReader) raw(key string) string {
	value, _ := r.lookup(key)
	return value
}

func (r *settingsReader) text(key, fallback string) string {
	if value := strings.TrimSpace(r.raw(key)); value != "" {
		return value
	}
	return fallback
}

func (r *settingsReader) integer(key string, fallback int) int {
	raw := r.text(key, "")
	if raw == "" {
		return fallback
	}
	value, err := strconv.Atoi(raw)
	if err != nil {
		r.fail(key, err)
	}
	return value
}

func (r *settingsReader) millis(key string, fallback int) time.Duration {
	value := r.integer(key, fallback)
	if value <= 0 {
		r.fail(key, errNotPositive)
	}
	return time.Duration(value) * time.Millisecond
}

func (r *settingsReader) count(key string, fallback int) int {
	value := r.integer(key, fallback)
	if value < 0 {
		r.fail(key, errNegative)
	}
	return value
}

func (r *settingsReader) boolean(key string) bool {
	raw := r.text(key, "")
	if raw == "" {
		return false
	}
	value, err := strconv.ParseBool(raw)
	if err != nil {
		r.fail(key, err)
	}
	return value
}

func (r *settingsReader) absoluteURL(key, raw string) {
	if raw == "" {
		return
	}
	parsed, err := url.Parse(raw)
	if err != nil || !parsed.IsAbs() || parsed.Host == "" {
		r.fail(key, errNotAbsoluteURL)
	}
}

func (r *settingsReader) fail(key string, err error) {
	r.errs = append(r.errs, fmt.Errorf("%s: %w", key, err))
}
