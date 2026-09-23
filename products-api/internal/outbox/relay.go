package outbox

import (
	"context"
	"log/slog"
	"time"
)

const (
	publishBudgetDivisor = 2
	logCycleFailed       = "outbox relay cycle failed"
	logReleaseFailed     = "outbox event could not be released"
	logConfirmFailed     = "outbox event could not be confirmed"
	logKeyError          = "error"
	logKeyEventID        = "eventId"
)

type PendingEvent struct {
	ID       string
	Key      string
	Payload  []byte
	Attempts int
}

type Claim struct {
	Limit      int
	Now        time.Time
	LeaseUntil time.Time
	Owner      string
}

type Store interface {
	Claim(ctx context.Context, claim Claim) ([]PendingEvent, error)
	MarkPublished(ctx context.Context, id, owner string, at time.Time) (bool, error)
	Release(ctx context.Context, id, owner string, attempts int, availableAt time.Time) (bool,
		error)
	CountUnpublished(ctx context.Context) (int64, error)
}

type Message struct {
	Key   []byte
	Value []byte
}

type Publisher interface {
	Publish(ctx context.Context, messages []Message) []error
}

type Recorder interface {
	OutboxPending(count int64)
	OutboxPublished(count int)
	OutboxFailed(count int)
}

type Settings struct {
	Interval   time.Duration
	BatchSize  int
	Lease      time.Duration
	RetryDelay time.Duration
	Owner      string
}

type Relay struct {
	store     Store
	publisher Publisher
	recorder  Recorder
	settings  Settings
	clock     func() time.Time
	logger    *slog.Logger
}

func NewRelay(store Store, publisher Publisher, recorder Recorder, settings Settings,
	clock func() time.Time, logger *slog.Logger,
) *Relay {
	return &Relay{store: store, publisher: publisher, recorder: recorder, settings: settings,
		clock: clock, logger: logger}
}

func (r *Relay) Run(ctx context.Context) {
	ticker := time.NewTicker(r.settings.Interval)
	defer ticker.Stop()
	for {
		if _, err := r.Cycle(ctx); err != nil && ctx.Err() == nil {
			r.logger.WarnContext(ctx, logCycleFailed, logKeyError, err.Error())
		}
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
		}
	}
}

func (r *Relay) Cycle(ctx context.Context) (int, error) {
	now := r.clock()
	batch, err := r.store.Claim(ctx, Claim{Limit: r.settings.BatchSize, Now: now,
		LeaseUntil: now.Add(r.settings.Lease), Owner: r.settings.Owner})
	if err != nil {
		return 0, err
	}
	published := r.publish(ctx, batch)
	pending, err := r.store.CountUnpublished(ctx)
	if err != nil {
		return published, err
	}
	r.recorder.OutboxPending(pending)
	return published, nil
}

func (r *Relay) publish(ctx context.Context, batch []PendingEvent) int {
	if len(batch) == 0 {
		return 0
	}
	sendCtx, cancel := context.WithTimeout(ctx, r.settings.Lease/publishBudgetDivisor)
	defer cancel()
	messages := make([]Message, 0, len(batch))
	for _, event := range batch {
		messages = append(messages, Message{Key: []byte(event.Key), Value: event.Payload})
	}
	results := r.publisher.Publish(sendCtx, messages)
	published, failed := 0, 0
	for i, event := range batch {
		if results[i] == nil && r.confirm(ctx, event) {
			published++
			continue
		}
		if results[i] != nil {
			failed++
			r.release(ctx, event)
		}
	}
	r.recorder.OutboxPublished(published)
	r.recorder.OutboxFailed(failed)
	return published
}

func (r *Relay) confirm(ctx context.Context, event PendingEvent) bool {
	confirmed, err := r.store.MarkPublished(ctx, event.ID, r.settings.Owner, r.clock())
	if err != nil {
		r.logger.WarnContext(ctx, logConfirmFailed, logKeyEventID, event.ID, logKeyError,
			err.Error())
	}
	return confirmed
}

func (r *Relay) release(ctx context.Context, event PendingEvent) {
	availableAt := r.clock().Add(r.settings.RetryDelay)
	_, err := r.store.Release(ctx, event.ID, r.settings.Owner, event.Attempts+1, availableAt)
	if err != nil {
		r.logger.WarnContext(ctx, logReleaseFailed, logKeyEventID, event.ID, logKeyError,
			err.Error())
	}
}
