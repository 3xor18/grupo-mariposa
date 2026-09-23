import { MongoDBContainer, StartedMongoDBContainer } from '@testcontainers/mongodb';
import { createServer } from 'node:net';
import { GenericContainer, StartedTestContainer, Wait } from 'testcontainers';

export const MONGO_IMAGE = 'mongo:7.0.26';
export const KAFKA_IMAGE = 'apache/kafka:3.9.1';
export const RYUK_IMAGE = 'testcontainers/ryuk:0.12.0';
export const MONGO_URI_VARIABLE = 'TEST_MONGODB_URI';
const KAFKA_PORT = 9092;
const KAFKA_READY_LOG = /Kafka Server started/;

export function useLocalRyukImage(): void {
  process.env.RYUK_CONTAINER_IMAGE ??= RYUK_IMAGE;
}

export async function startMongo(): Promise<StartedMongoDBContainer> {
  useLocalRyukImage();
  return new MongoDBContainer(MONGO_IMAGE).start();
}

export function mongoUriOf(container: StartedMongoDBContainer): string {
  return `${container.getConnectionString()}/?directConnection=true`;
}

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const server = createServer();
    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const address = server.address();
      const port = typeof address === 'object' && address !== null ? address.port : 0;
      server.close(() => {
        resolve(port);
      });
    });
  });
}

export interface StartedKafka {
  readonly container: StartedTestContainer;
  readonly bootstrapServer: string;
}

export async function startKafka(): Promise<StartedKafka> {
  useLocalRyukImage();
  const port = await freePort();
  const container = await new GenericContainer(KAFKA_IMAGE)
    .withExposedPorts({ container: KAFKA_PORT, host: port })
    .withEnvironment({
      KAFKA_NODE_ID: '1',
      KAFKA_PROCESS_ROLES: 'broker,controller',
      KAFKA_LISTENERS: `PLAINTEXT://:${String(KAFKA_PORT)},CONTROLLER://:9093`,
      KAFKA_ADVERTISED_LISTENERS: `PLAINTEXT://localhost:${String(port)}`,
      KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER',
      KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT',
      KAFKA_CONTROLLER_QUORUM_VOTERS: '1@localhost:9093',
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: '1',
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: '1',
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: '1',
      KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: '0',
      KAFKA_NUM_PARTITIONS: '3',
    })
    .withWaitStrategy(Wait.forLogMessage(KAFKA_READY_LOG))
    .start();
  return { container, bootstrapServer: `localhost:${String(port)}` };
}
