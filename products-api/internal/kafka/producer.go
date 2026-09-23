package kafka

import (
	"context"
	"fmt"

	"github.com/twmb/franz-go/pkg/kgo"

	"github.com/grupomariposa/platform/products-api/internal/outbox"
)

type Settings struct {
	Brokers  []string
	Topic    string
	ClientID string
}

type Producer struct {
	client *kgo.Client
}

func NewProducer(settings Settings) (*Producer, error) {
	client, err := kgo.NewClient(
		kgo.SeedBrokers(settings.Brokers...),
		kgo.DefaultProduceTopic(settings.Topic),
		kgo.ClientID(settings.ClientID),
		kgo.RequiredAcks(kgo.AllISRAcks()),
		kgo.RecordPartitioner(kgo.StickyKeyPartitioner(nil)),
	)
	if err != nil {
		return nil, fmt.Errorf("create kafka producer: %w", err)
	}
	return &Producer{client: client}, nil
}

func (p *Producer) Publish(ctx context.Context, messages []outbox.Message) []error {
	records := make([]*kgo.Record, 0, len(messages))
	positions := make(map[*kgo.Record]int, len(messages))
	for i, m := range messages {
		record := &kgo.Record{Key: m.Key, Value: m.Value}
		records = append(records, record)
		positions[record] = i
	}
	errs := make([]error, len(messages))
	for _, result := range p.client.ProduceSync(ctx, records...) {
		errs[positions[result.Record]] = result.Err
	}
	return errs
}

func (p *Producer) Close() {
	if p != nil {
		p.client.Close()
	}
}
