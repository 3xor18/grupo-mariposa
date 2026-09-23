package config

import (
	"errors"
	"strings"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/market"
)

const (
	EnvPlatformMarkets    = "PLATFORM_MARKETS"
	EnvPlatformCurrencies = "PLATFORM_CURRENCIES"
	EnvSeedEnabled        = "SEED_ENABLED"
	EnvKafkaTLSEnabled    = "KAFKA_TLS_ENABLED"
	EnvOutboxRetention    = "OUTBOX_RETENTION"
	defaultRetention      = "7d"
	EnvStorageDriver      = "STORAGE_DRIVER"
	EnvMongoURI           = "MONGODB_URI"
	EnvMongoDatabase      = "MONGODB_DATABASE"
	EnvMongoTimeoutMS     = "MONGODB_TIMEOUT_MS"
	EnvKafkaBootstrap     = "KAFKA_BOOTSTRAP_SERVERS"
	EnvKafkaTopic         = "KAFKA_TOPIC_CHANGES"
	EnvOutboxIntervalMS   = "OUTBOX_RELAY_INTERVAL_MS"
	EnvOutboxBatchSize    = "OUTBOX_BATCH_SIZE"
	EnvOutboxLeaseMS      = "OUTBOX_LEASE_MS"
	EnvOutboxRetryDelayMS = "OUTBOX_RETRY_DELAY_MS"
	StorageMongo          = "mongo"
	StorageMemory         = "memory"
	defaultDatabase       = "products"
	defaultMongoTimeoutMS = 5000
	defaultTopic          = "products.changed.v1"
	defaultIntervalMS     = 250
	defaultBatchSize      = 100
	maxBatchSize          = 1000
	defaultLeaseMS        = 30000
	defaultRetryDelayMS   = 1000
	listSeparator         = ","
)

var (
	errUnknownDriver      = errors.New("must be " + StorageMongo + " or " + StorageMemory)
	errMemoryInProduction = errors.New("must not be " + StorageMemory + " when " +
		EnvAppEnvironment + "=" + productionEnvironment)
	errTrueInProduction = errors.New("must not be true when " + EnvAppEnvironment + "=" +
		productionEnvironment)
	errFalseInProduction = errors.New("must be true when " + EnvAppEnvironment + "=" +
		productionEnvironment)
)

type Storage struct {
	Driver   string
	Seed     bool
	MongoURI string
	Database string
	Timeout  time.Duration
}

type Kafka struct {
	Brokers []string
	Topic   string
	TLS     bool
}

type Outbox struct {
	Interval   time.Duration
	BatchSize  int
	Lease      time.Duration
	RetryDelay time.Duration
	Retention  time.Duration
}

func loadMarkets(r *Reader) market.Catalog {
	catalog, err := market.Parse(r.String(EnvPlatformMarkets, market.DefaultMarkets),
		r.String(EnvPlatformCurrencies, market.DefaultCurrencies))
	if err != nil {
		r.Fail(EnvPlatformMarkets+"/"+EnvPlatformCurrencies, err)
	}
	return catalog
}

func loadStorage(r *Reader) Storage {
	storage := Storage{
		Driver:   r.String(EnvStorageDriver, StorageMongo),
		Seed:     r.Bool(EnvSeedEnabled, false),
		MongoURI: r.String(EnvMongoURI, ""),
		Database: r.String(EnvMongoDatabase, defaultDatabase),
		Timeout:  r.Millis(EnvMongoTimeoutMS, defaultMongoTimeoutMS),
	}
	if storage.Seed && production(r) {
		r.Fail(EnvSeedEnabled, errTrueInProduction)
	}
	switch storage.Driver {
	case StorageMongo:
		r.Require(EnvMongoURI, storage.MongoURI)
	case StorageMemory:
		if production(r) {
			r.Fail(EnvStorageDriver, errMemoryInProduction)
		}
	default:
		r.Fail(EnvStorageDriver, errUnknownDriver)
	}
	return storage
}

func loadKafka(r *Reader, storage Storage) Kafka {
	kafka := Kafka{
		Brokers: splitList(r.String(EnvKafkaBootstrap, "")),
		Topic:   r.String(EnvKafkaTopic, defaultTopic),
		TLS:     r.Bool(EnvKafkaTLSEnabled, false),
	}
	if !kafka.TLS && production(r) {
		r.Fail(EnvKafkaTLSEnabled, errFalseInProduction)
	}
	if storage.Driver == StorageMongo && len(kafka.Brokers) == 0 {
		r.Fail(EnvKafkaBootstrap, errRequired)
	}
	return kafka
}

func loadOutbox(r *Reader) Outbox {
	return Outbox{
		Interval:   r.Millis(EnvOutboxIntervalMS, defaultIntervalMS),
		BatchSize:  r.Int(EnvOutboxBatchSize, defaultBatchSize, minPositive, maxBatchSize),
		Lease:      r.Millis(EnvOutboxLeaseMS, defaultLeaseMS),
		RetryDelay: r.Millis(EnvOutboxRetryDelayMS, defaultRetryDelayMS),
		Retention:  r.Duration(EnvOutboxRetention, defaultRetention),
	}
}

func production(r *Reader) bool {
	return strings.EqualFold(r.String(EnvAppEnvironment, ""), productionEnvironment)
}

func splitList(raw string) []string {
	var items []string
	for _, item := range strings.Split(raw, listSeparator) {
		if trimmed := strings.TrimSpace(item); trimmed != "" {
			items = append(items, trimmed)
		}
	}
	return items
}
