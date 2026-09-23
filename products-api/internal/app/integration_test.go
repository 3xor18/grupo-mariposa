package app_test

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"github.com/getkin/kin-openapi/openapi3"
	"github.com/twmb/franz-go/pkg/kgo"

	"github.com/grupomariposa/platform/products-api/internal/config"
	"github.com/grupomariposa/platform/products-api/internal/testsupport/containers"
)

const (
	changesTopic     = "products.changed.v1"
	eventSchemaPath  = "../../../contracts/events/products.changed.v1.schema.json"
	integrationLimit = 60 * time.Second
)

type infrastructure struct {
	mongoURI string
	brokers  string
	err      error
}

var (
	infraOnce sync.Once
	infra     infrastructure
	stops     []containers.Stop
)

func TestMain(m *testing.M) {
	code := m.Run()
	for _, stop := range stops {
		stop()
	}
	os.Exit(code)
}

func requireInfrastructure(t *testing.T) infrastructure {
	t.Helper()
	if !containers.Enabled() {
		t.Skip("integration tests disabled")
	}
	infraOnce.Do(func() {
		ctx := context.Background()
		uri, stopMongo, err := containers.StartMongo(ctx)
		stops = append(stops, stopMongo)
		kafka, stopKafka, kafkaErr := containers.StartKafka(ctx)
		stops = append(stops, stopKafka)
		if err == nil && kafkaErr == nil {
			kafkaErr = kafka.CreateTopic(ctx, changesTopic)
		}
		infra = infrastructure{mongoURI: uri, err: firstError(err, kafkaErr)}
		if kafka != nil {
			infra.brokers = kafka.Brokers
		}
	})
	if infra.err != nil {
		t.Fatalf("start infrastructure: %v", infra.err)
	}
	return infra
}

func firstError(errs ...error) error {
	for _, err := range errs {
		if err != nil {
			return err
		}
	}
	return nil
}

func TestPatchPublishesChangeEventThroughOutbox(t *testing.T) {
	infra := requireInfrastructure(t)
	inst := start(t, baseEnv(
		config.EnvStorageDriver, config.StorageMongo, config.EnvMongoURI, infra.mongoURI,
		config.EnvMongoDatabase, "products_it", config.EnvKafkaBootstrap, infra.brokers,
		config.EnvOutboxIntervalMS, "50"))
	if status := healthStatus(t, inst.url+"/health/ready"); status != "UP" {
		t.Fatalf("want ready with mongo reachable, got %q", status)
	}
	code, body := patch(t, inst.url+"/products/PRD-020?market=MX", `"1"`,
		`{"status":"DISCONTINUED"}`)
	if code != http.StatusOK || !strings.Contains(body, `"version":2`) {
		t.Fatalf("want 200 with version 2, got %d %s", code, body)
	}
	record := consumeChange(t, infra.brokers, "MX:PRD-020")
	assertMatchesSchema(t, record.Value)
	var event map[string]any
	_ = json.Unmarshal(record.Value, &event)
	if event["version"] != 2.0 || event["status"] != "DISCONTINUED" || event["market"] != "MX" {
		t.Fatalf("unexpected event %v", event)
	}
	if code, _ := patch(t, inst.url+"/products/PRD-020?market=MX", `"1"`,
		`{"status":"ACTIVE"}`); code != http.StatusPreconditionFailed {
		t.Fatalf("stale If-Match must return 412, got %d", code)
	}
	waitForMetric(t, inst.url, "outbox_published_total 1")
	if code := inst.stop(t); code != 0 {
		t.Fatalf("want clean shutdown closing mongo and kafka, got %d", code)
	}
}

func patch(t *testing.T, url, ifMatch, body string) (int, string) {
	t.Helper()
	req, err := http.NewRequestWithContext(t.Context(), http.MethodPatch, url,
		strings.NewReader(body))
	if err != nil {
		t.Fatalf("request: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("If-Match", ifMatch)
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		t.Fatalf("patch: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()
	raw, _ := io.ReadAll(resp.Body)
	return resp.StatusCode, string(raw)
}

func consumeChange(t *testing.T, brokers, key string) *kgo.Record {
	t.Helper()
	consumer, err := kgo.NewClient(kgo.SeedBrokers(brokers), kgo.ConsumeTopics(changesTopic),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()))
	if err != nil {
		t.Fatalf("consumer: %v", err)
	}
	defer consumer.Close()
	ctx, cancel := context.WithTimeout(t.Context(), integrationLimit)
	defer cancel()
	for {
		fetches := consumer.PollFetches(ctx)
		if ctx.Err() != nil {
			t.Fatalf("no event with key %s published", key)
		}
		for _, record := range fetches.Records() {
			if string(record.Key) == key {
				return record
			}
		}
	}
}

func assertMatchesSchema(t *testing.T, payload []byte) {
	t.Helper()
	raw, err := os.ReadFile(eventSchemaPath)
	if err != nil {
		t.Fatalf("read schema: %v", err)
	}
	var schema openapi3.Schema
	if err := json.Unmarshal(raw, &schema); err != nil {
		t.Fatalf("decode schema: %v", err)
	}
	var value any
	if err := json.Unmarshal(payload, &value); err != nil {
		t.Fatalf("decode event: %v", err)
	}
	if err := schema.VisitJSON(value, openapi3.EnableFormatValidation()); err != nil {
		t.Fatalf("event violates products.changed.v1: %v\n%s", err, payload)
	}
}

func waitForMetric(t *testing.T, url, line string) {
	t.Helper()
	deadline := time.Now().Add(integrationLimit)
	for {
		_, body := get(t, url+"/metrics")
		if strings.Contains(body, line) {
			return
		}
		if time.Now().After(deadline) {
			t.Fatalf("metric %q not exposed", line)
		}
		time.Sleep(pollInterval)
	}
}
