package remote_test

import (
	"errors"
	"strings"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/config/remote"
)

func TestLoadSettingsDefaults(t *testing.T) {
	s, err := remote.LoadSettings(envOf(map[string]string{}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := remote.Settings{AppName: "products-api", Profile: "default",
		Timeout: 3 * time.Second, Retries: 3, Backoff: 200 * time.Millisecond}
	if s != want || s.Enabled() {
		t.Fatalf("want %+v, got %+v", want, s)
	}
}

func TestLoadSettingsOverrides(t *testing.T) {
	s, err := remote.LoadSettings(envOf(map[string]string{
		remote.EnvURL: "http://config:8888//", remote.EnvAppName: "catalog",
		remote.EnvTimeoutMS: "500", remote.EnvRetries: "0", remote.EnvFailFast: "true",
	}))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if s.BaseURL != "http://config:8888" || s.AppName != "catalog" || !s.Enabled() ||
		s.Timeout != 500*time.Millisecond || s.Retries != 0 || !s.FailFast {
		t.Fatalf("overrides not applied: %+v", s)
	}
}

func TestLoadSettingsRejectsInvalid(t *testing.T) {
	cases := map[string]string{
		remote.EnvURL:       "/relative",
		remote.EnvTimeoutMS: "0",
		remote.EnvRetries:   "-1",
		remote.EnvBackoffMS: "abc",
		remote.EnvFailFast:  "maybe",
	}
	for key, value := range cases {
		t.Run(key, func(t *testing.T) {
			_, err := remote.LoadSettings(envOf(map[string]string{key: value}))
			if !errors.Is(err, remote.ErrInvalidSettings) || !strings.Contains(err.Error(), key) {
				t.Fatalf("want invalid %s, got %v", key, err)
			}
		})
	}
}

func TestRedact(t *testing.T) {
	safe := remote.Redact(map[string]string{
		"AUTH_CLIENT_SECRET": "a", "DB_PASSWORD": "b", "API_KEY": "c", "ACCESS_TOKEN": "d",
		"RATE_LIMIT_RPS": "200",
	})
	for key, value := range safe {
		if remote.IsSensitive(key) == (value != "******") {
			t.Fatalf("%s: unexpected value %q", key, value)
		}
	}
	if safe["RATE_LIMIT_RPS"] != "200" {
		t.Fatal("non sensitive values must be kept")
	}
}
