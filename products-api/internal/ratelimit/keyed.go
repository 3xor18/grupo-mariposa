package ratelimit

import (
	"container/list"
	"sync"
	"time"

	"golang.org/x/time/rate"
)

type Keyed struct {
	mu       sync.Mutex
	limit    rate.Limit
	burst    int
	capacity int
	order    *list.List
	entries  map[string]*list.Element
}

type entry struct {
	key     string
	limiter *rate.Limiter
}

func NewKeyed(rps float64, burst, capacity int) *Keyed {
	return &Keyed{
		limit:    rate.Limit(rps),
		burst:    burst,
		capacity: capacity,
		order:    list.New(),
		entries:  make(map[string]*list.Element, capacity),
	}
}

func (k *Keyed) Delay(key string) time.Duration {
	reservation := k.limiterFor(key).Reserve()
	delay := reservation.Delay()
	if delay > 0 {
		reservation.Cancel()
	}
	return delay
}

func (k *Keyed) limiterFor(key string) *rate.Limiter {
	k.mu.Lock()
	defer k.mu.Unlock()
	if element, ok := k.entries[key]; ok {
		k.order.MoveToFront(element)
		return element.Value.(*entry).limiter
	}
	if k.order.Len() >= k.capacity {
		k.evictOldest()
	}
	created := &entry{key: key, limiter: rate.NewLimiter(k.limit, k.burst)}
	k.entries[key] = k.order.PushFront(created)
	return created.limiter
}

func (k *Keyed) evictOldest() {
	oldest := k.order.Back()
	k.order.Remove(oldest)
	delete(k.entries, oldest.Value.(*entry).key)
}
