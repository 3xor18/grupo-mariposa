package containers

import (
	"context"
	"errors"
	"fmt"
	"os"
	"strings"
	"time"

	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/mongodb"
	"github.com/testcontainers/testcontainers-go/wait"
)

const (
	MongoImage        = "mongo:7.0.26"
	KafkaImage        = "apache/kafka:3.9.1"
	replicaSet        = "rs0"
	mongoPort         = "27017/tcp"
	kafkaPort         = "9092/tcp"
	advertisedFile    = "/tmp/advertised-listeners"
	kafkaStartedLog   = "Kafka Server started"
	startupTimeout    = 2 * time.Minute
	ryukDisabledEnv   = "TESTCONTAINERS_RYUK_DISABLED"
	skipEnv           = "SKIP_INTEGRATION"
	topicPartitions   = "3"
	listenersFileMode = 0o644
	advertisedPattern = "PLAINTEXT://%s:%s,BROKER://localhost:9093"
	listenerSeparator = ","
	kafkaStartCommand = "while [ ! -f " + advertisedFile + " ]; do sleep 0.1; done; " +
		"export KAFKA_ADVERTISED_LISTENERS=$(cat " + advertisedFile + "); " +
		"exec /etc/kafka/docker/run"
)

var ErrTopicNotCreated = errors.New("kafka topic not created")

type Stop func()

type Kafka struct {
	Brokers   string
	container testcontainers.Container
}

func Enabled() bool {
	return os.Getenv(skipEnv) == ""
}

func StartMongo(ctx context.Context) (string, Stop, error) {
	disableReaper()
	container, err := mongodb.Run(ctx, MongoImage, mongodb.WithReplicaSet(replicaSet))
	stop := terminator(container)
	if err != nil {
		return "", stop, fmt.Errorf("start mongo: %w", err)
	}
	endpoint, err := container.PortEndpoint(ctx, mongoPort, "")
	if err != nil {
		return "", stop, fmt.Errorf("mongo endpoint: %w", err)
	}
	return "mongodb://" + endpoint + "/?directConnection=true", stop, nil
}

func StartKafka(ctx context.Context) (*Kafka, Stop, error) {
	disableReaper()
	container, err := testcontainers.GenericContainer(ctx,
		testcontainers.GenericContainerRequest{ContainerRequest: kafkaRequest(), Started: true})
	stop := terminator(container)
	if err != nil {
		return nil, stop, fmt.Errorf("start kafka: %w", err)
	}
	endpoint, err := container.PortEndpoint(ctx, kafkaPort, "")
	if err != nil {
		return nil, stop, fmt.Errorf("kafka endpoint: %w", err)
	}
	return &Kafka{Brokers: endpoint, container: container}, stop, nil
}

func (k *Kafka) CreateTopic(ctx context.Context, topic string) error {
	code, _, err := k.container.Exec(ctx, []string{
		"/opt/kafka/bin/kafka-topics.sh", "--bootstrap-server", "localhost:9093", "--create",
		"--if-not-exists", "--topic", topic, "--partitions", topicPartitions,
	})
	if err != nil || code != 0 {
		return fmt.Errorf("%w: %s exit %d: %w", ErrTopicNotCreated, topic, code, err)
	}
	return nil
}

func kafkaRequest() testcontainers.ContainerRequest {
	listeners := strings.Join([]string{"PLAINTEXT://0.0.0.0:9092", "BROKER://0.0.0.0:9093",
		"CONTROLLER://0.0.0.0:9094"}, listenerSeparator)
	protocols := strings.Join([]string{"CONTROLLER:PLAINTEXT", "PLAINTEXT:PLAINTEXT",
		"BROKER:PLAINTEXT"}, listenerSeparator)
	return testcontainers.ContainerRequest{
		Image:        KafkaImage,
		ExposedPorts: []string{kafkaPort},
		Env: map[string]string{
			"KAFKA_NODE_ID":                                  "1",
			"KAFKA_PROCESS_ROLES":                            "broker,controller",
			"KAFKA_LISTENERS":                                listeners,
			"KAFKA_LISTENER_SECURITY_PROTOCOL_MAP":           protocols,
			"KAFKA_INTER_BROKER_LISTENER_NAME":               "BROKER",
			"KAFKA_CONTROLLER_LISTENER_NAMES":                "CONTROLLER",
			"KAFKA_CONTROLLER_QUORUM_VOTERS":                 "1@localhost:9094",
			"KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR":         "1",
			"KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR": "1",
			"KAFKA_TRANSACTION_STATE_LOG_MIN_ISR":            "1",
			"KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS":         "0",
		},
		Entrypoint: []string{"sh"},
		Cmd:        []string{"-c", kafkaStartCommand},
		LifecycleHooks: []testcontainers.ContainerLifecycleHooks{{
			PostStarts: []testcontainers.ContainerHook{advertiseAndWait},
		}},
	}
}

func advertiseAndWait(ctx context.Context, c testcontainers.Container) error {
	host, err := c.Host(ctx)
	if err != nil {
		return fmt.Errorf("kafka host: %w", err)
	}
	port, err := c.MappedPort(ctx, kafkaPort)
	if err != nil {
		return fmt.Errorf("kafka port: %w", err)
	}
	listeners := fmt.Sprintf(advertisedPattern, host, port.Port())
	err = c.CopyToContainer(ctx, []byte(listeners), advertisedFile, listenersFileMode)
	if err != nil {
		return fmt.Errorf("advertise listeners: %w", err)
	}
	return wait.ForLog(kafkaStartedLog).WithStartupTimeout(startupTimeout).WaitUntilReady(ctx, c)
}

func disableReaper() {
	if os.Getenv(ryukDisabledEnv) == "" {
		_ = os.Setenv(ryukDisabledEnv, "true")
	}
}

func terminator(container testcontainers.Container) Stop {
	return func() {
		_ = testcontainers.TerminateContainer(container)
	}
}
