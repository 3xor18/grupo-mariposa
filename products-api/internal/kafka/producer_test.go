package kafka_test

import (
	"context"
	"fmt"
	"os"
	"testing"
	"time"

	"github.com/twmb/franz-go/pkg/kgo"

	"github.com/grupomariposa/platform/products-api/internal/kafka"
	"github.com/grupomariposa/platform/products-api/internal/outbox"
	"github.com/grupomariposa/platform/products-api/internal/testsupport/containers"
)

const (
	topic       = "products.changed.v1"
	pollTimeout = 30 * time.Second
)

var broker *containers.Kafka

func TestMain(m *testing.M) {
	os.Exit(run(m))
}

func run(m *testing.M) int {
	if !containers.Enabled() {
		return m.Run()
	}
	started, stop, err := containers.StartKafka(context.Background())
	defer stop()
	if err == nil {
		err = started.CreateTopic(context.Background(), topic)
	}
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 1
	}
	broker = started
	return m.Run()
}

func requireBroker(t *testing.T) {
	t.Helper()
	if broker == nil {
		t.Skip("integration tests disabled")
	}
}

func TestPublishDeliversKeyedRecords(t *testing.T) {
	requireBroker(t)
	producer, err := kafka.NewProducer(kafka.Settings{Brokers: []string{broker.Brokers},
		Topic: topic, ClientID: "products-api-test"})
	if err != nil {
		t.Fatalf("producer: %v", err)
	}
	defer producer.Close()
	messages := []outbox.Message{
		{Key: []byte("MX:PRD-001"), Value: []byte(`{"version":2}`)},
		{Key: []byte("CL:PRD-015"), Value: []byte(`{"version":3}`)},
	}
	for i, err := range producer.Publish(t.Context(), messages) {
		if err != nil {
			t.Fatalf("message %d: %v", i, err)
		}
	}
	received := consume(t, len(messages))
	for _, m := range messages {
		if received[string(m.Key)] != string(m.Value) {
			t.Fatalf("key %s: want %s, got %q", m.Key, m.Value, received[string(m.Key)])
		}
	}
}

func consume(t *testing.T, count int) map[string]string {
	t.Helper()
	consumer, err := kgo.NewClient(kgo.SeedBrokers(broker.Brokers), kgo.ConsumeTopics(topic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()))
	if err != nil {
		t.Fatalf("consumer: %v", err)
	}
	defer consumer.Close()
	ctx, cancel := context.WithTimeout(t.Context(), pollTimeout)
	defer cancel()
	received := map[string]string{}
	for len(received) < count {
		fetches := consumer.PollFetches(ctx)
		if ctx.Err() != nil {
			t.Fatalf("received %d of %d records", len(received), count)
		}
		fetches.EachRecord(func(r *kgo.Record) { received[string(r.Key)] = string(r.Value) })
	}
	return received
}

func TestPublishReportsFailuresPerMessage(t *testing.T) {
	producer, err := kafka.NewProducer(kafka.Settings{Brokers: []string{"127.0.0.1:1"},
		Topic: topic})
	if err != nil {
		t.Fatalf("producer: %v", err)
	}
	defer producer.Close()
	ctx, cancel := context.WithTimeout(t.Context(), 200*time.Millisecond)
	defer cancel()
	errs := producer.Publish(ctx, []outbox.Message{{Key: []byte("a")}, {Key: []byte("b")}})
	if len(errs) != 2 || errs[0] == nil || errs[1] == nil {
		t.Fatalf("want a failure per message, got %v", errs)
	}
}

func TestTLSProducerFailsAgainstPlaintextListener(t *testing.T) {
	producer, err := kafka.NewProducer(kafka.Settings{Brokers: []string{"127.0.0.1:1"},
		Topic: topic, TLS: true})
	if err != nil {
		t.Fatalf("producer: %v", err)
	}
	defer producer.Close()
	ctx, cancel := context.WithTimeout(t.Context(), 200*time.Millisecond)
	defer cancel()
	if errs := producer.Publish(ctx, []outbox.Message{{Key: []byte("a")}}); errs[0] == nil {
		t.Fatal("want delivery failure")
	}
}

func TestCloseIsNilSafe(_ *testing.T) {
	var producer *kafka.Producer
	producer.Close()
}

func TestNewProducerRejectsInvalidSettings(t *testing.T) {
	if _, err := kafka.NewProducer(kafka.Settings{Topic: topic}); err ==
		nil {
		t.Fatal("want missing seed brokers error")
	}
}
