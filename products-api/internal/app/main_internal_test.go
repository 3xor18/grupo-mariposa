package app

import (
	"context"
	"errors"
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

func TestWrapCloseKeepsCause(t *testing.T) {
	cause := errors.New("disconnect failed")
	if err := wrapClose(cause); !errors.Is(err, cause) {
		t.Fatalf("want wrapped cause, got %v", err)
	}
	if wrapClose(nil) != nil {
		t.Fatal("nil must stay nil")
	}
}
