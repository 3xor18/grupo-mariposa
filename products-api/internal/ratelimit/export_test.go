package ratelimit

func (k *Keyed) Len() int {
	k.mu.Lock()
	defer k.mu.Unlock()
	return k.order.Len()
}
