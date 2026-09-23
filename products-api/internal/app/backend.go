package app

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"

	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/kafka"
	"github.com/grupomariposa/platform/products-api/internal/outbox"
	"github.com/grupomariposa/platform/products-api/internal/product"
	"github.com/grupomariposa/platform/products-api/internal/seed"
	"github.com/grupomariposa/platform/products-api/internal/storage/memory"
	"github.com/grupomariposa/platform/products-api/internal/storage/mongodb"
	"github.com/grupomariposa/platform/products-api/internal/telemetry"
)

const ownerSeparator = "-"

type backend struct {
	repository product.Repository
	writer     product.Writer
	ping       func(ctx context.Context) error
	relay      *outbox.Relay
	close      func(ctx context.Context) error
}

func openBackend(ctx context.Context, cfg config.Config, logger *slog.Logger,
	recorder outbox.Recorder,
) (backend, error) {
	if cfg.Storage.Driver == config.StorageMemory {
		repo := memory.NewRepository(seedRows(cfg.Storage))
		return backend{repository: repo, writer: repo, ping: alwaysReachable,
			close: alwaysReachable}, nil
	}
	return mongoBackend(ctx, cfg, logger, recorder)
}

func mongoBackend(ctx context.Context, cfg config.Config, logger *slog.Logger,
	recorder outbox.Recorder,
) (backend, error) {
	store, err := mongodb.Open(mongodb.Settings{URI: cfg.Storage.MongoURI,
		Database: cfg.Storage.Database, Topic: cfg.Kafka.Topic, Timeout: cfg.Storage.Timeout,
		Retention: cfg.Outbox.Retention})
	if err != nil {
		return backend{}, err
	}
	producer, producerErr := kafka.NewProducer(kafka.Settings{Brokers: cfg.Kafka.Brokers,
		Topic: cfg.Kafka.Topic, ClientID: telemetry.ServiceName, TLS: cfg.Kafka.TLS})
	if err := errors.Join(producerErr, store.Setup(ctx, seedRows(cfg.Storage))); err != nil {
		producer.Close()
		return backend{}, errors.Join(fmt.Errorf("prepare mongodb backend: %w", err),
			store.Close(context.WithoutCancel(ctx)))
	}
	relay := outbox.NewRelay(store, producer, recorder, relaySettings(cfg.Outbox), time.Now,
		logger)
	return backend{repository: store, writer: store, ping: store.Ping, relay: relay,
		close: func(ctx context.Context) error {
			producer.Close()
			return store.Close(ctx)
		}}, nil
}

func seedRows(storage config.Storage) []product.Product {
	if !storage.Seed {
		return nil
	}
	return seed.Products()
}

func relaySettings(o config.Outbox) outbox.Settings {
	return outbox.Settings{
		Interval:   o.Interval,
		BatchSize:  o.BatchSize,
		Lease:      o.Lease,
		RetryDelay: o.RetryDelay,
		Owner:      telemetry.ServiceName + ownerSeparator + uuid.NewString(),
	}
}

func newEventID() (string, error) {
	id, err := uuid.NewV7()
	return id.String(), err
}

func alwaysReachable(context.Context) error {
	return nil
}
