package remote

import (
	"strings"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/config"
)

const (
	EnvURL          = "CONFIG_SERVER_URL"
	EnvAppName      = "CONFIG_APP_NAME"
	EnvProfile      = "CONFIG_PROFILE"
	EnvUsername     = "CONFIG_SERVER_USERNAME"
	EnvPassword     = "CONFIG_SERVER_PASSWORD"
	EnvTimeoutMS    = "CONFIG_SERVER_TIMEOUT_MS"
	EnvRetries      = "CONFIG_SERVER_RETRIES"
	EnvBackoffMS    = "CONFIG_SERVER_BACKOFF_MS"
	EnvBackoffMaxMS = "CONFIG_SERVER_BACKOFF_MAX_MS"
	EnvFailFast     = "CONFIG_SERVER_FAIL_FAST"

	defaultAppName      = "products-api"
	defaultProfile      = "default"
	defaultTimeoutMS    = 3000
	defaultRetries      = 3
	maxRetries          = 10
	defaultBackoffMS    = 200
	defaultBackoffMaxMS = 2000
	urlPathSeparator    = "/"
)

type settings struct {
	baseURL    string
	appName    string
	profile    string
	username   string
	password   string
	timeout    time.Duration
	retries    int
	backoff    time.Duration
	backoffMax time.Duration
	failFast   bool
}

func (s settings) enabled() bool {
	return s.baseURL != ""
}

func loadSettings(lookup config.LookupFunc) (settings, error) {
	r := config.NewReader(lookup)
	s := settings{
		baseURL:    strings.TrimRight(r.AbsoluteURL(EnvURL, ""), urlPathSeparator),
		appName:    r.String(EnvAppName, defaultAppName),
		profile:    r.String(EnvProfile, defaultProfile),
		username:   r.String(EnvUsername, ""),
		password:   r.Raw(EnvPassword),
		timeout:    r.Millis(EnvTimeoutMS, defaultTimeoutMS),
		retries:    r.Int(EnvRetries, defaultRetries, 0, maxRetries),
		backoff:    r.Millis(EnvBackoffMS, defaultBackoffMS),
		backoffMax: r.Millis(EnvBackoffMaxMS, defaultBackoffMaxMS),
		failFast:   r.Bool(EnvFailFast, false),
	}
	if err := r.Err(); err != nil {
		return settings{}, err
	}
	return s, nil
}
