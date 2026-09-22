package ratelimit_test

import (
	"sync"
	"testing"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/ratelimit"
)

func TestDelayIsPerKey(t *testing.T) {
	limiter := ratelimit.NewKeyed(1, 1, 10)
	if limiter.Delay("a") != 0 || limiter.Delay("b") != 0 {
		t.Fatal("first request of every key must pass")
	}
	delay := limiter.Delay("a")
	if delay <= 0 || delay > time.Second {
		t.Fatalf("want delay in (0, 1s], got %v", delay)
	}
	if again := limiter.Delay("a"); again <= 0 || again > time.Second {
		t.Fatalf("rejected reservation must be cancelled, got %v", again)
	}
}

func TestLeastRecentlyUsedKeyIsEvicted(t *testing.T) {
	limiter := ratelimit.NewKeyed(1, 1, 2)
	limiter.Delay("a")
	limiter.Delay("b")
	limiter.Delay("a")
	limiter.Delay("c")
	if limiter.Len() != 2 {
		t.Fatalf("want bounded size 2, got %d", limiter.Len())
	}
	if limiter.Delay("b") != 0 {
		t.Fatal("evicted key must start with a fresh bucket")
	}
	if limiter.Delay("c") == 0 {
		t.Fatal("recent key must keep its bucket")
	}
}

func TestDelayIsConcurrencySafe(t *testing.T) {
	const callers, capacity = 64, 8
	limiter := ratelimit.NewKeyed(1000, 1000, capacity)
	var wg sync.WaitGroup
	for i := range callers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			limiter.Delay(string(rune('a' + i%26)))
		}()
	}
	wg.Wait()
	if limiter.Len() > capacity {
		t.Fatalf("size %d exceeds capacity %d", limiter.Len(), capacity)
	}
}
