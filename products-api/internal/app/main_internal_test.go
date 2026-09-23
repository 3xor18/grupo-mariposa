package app

import (
	"context"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/config"
)

func TestCheckHealthRejectsUnbuildableAndUnreachableTargets(t *testing.T) {
	settings := config.Probe{Timeout: time.Second}
	for _, url := range []string{"::bad", "http://127.0.0.1:1/health/live"} {
		if err := checkHealth(context.Background(), url, settings); err == nil {
			t.Fatalf("%s: want error", url)
		}
	}
}
