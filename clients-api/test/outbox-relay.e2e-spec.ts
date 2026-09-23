import { NestExpressApplication } from '@nestjs/platform-express';
import { Consumer, Kafka, KafkaMessage, logLevel } from 'kafkajs';
import { Db } from 'mongodb';
import request from 'supertest';
import { App } from 'supertest/types';
import { MONGO_DATABASE } from '../src/shared/mongo/mongo.tokens';
import { OUTBOX_COLLECTION, OutboxStatus } from '../src/shared/outbox/outbox.document';
import { StartedKafka, startKafka } from './support/containers';
import { createContractValidators } from './support/contracts';
import { createTestApp, NO_AUTH, testConfig } from './support/test-app';

const TOPIC = 'clients.changed.v1';
const PARTITIONS = 3;
const DELIVERY_TIMEOUT_MS = 60_000;
const POLL_MS = 100;

async function eventually(condition: () => boolean | Promise<boolean>): Promise<void> {
  const deadline = Date.now() + DELIVERY_TIMEOUT_MS;
  while (!(await condition())) {
    if (Date.now() > deadline) {
      throw new Error('condition not met before the deadline');
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_MS));
  }
}

describe('outbox relay publishing to Kafka', () => {
  const received: KafkaMessage[] = [];
  let kafka: StartedKafka;
  let consumer: Consumer;
  let app: NestExpressApplication;
  let server: App;

  beforeAll(async () => {
    kafka = await startKafka();
    const client = new Kafka({ brokers: [kafka.bootstrapServer], logLevel: logLevel.NOTHING });
    const admin = client.admin();
    await admin.connect();
    await admin.createTopics({ topics: [{ topic: TOPIC, numPartitions: PARTITIONS }] });
    await admin.disconnect();
    consumer = client.consumer({ groupId: 'relay-test' });
    await consumer.connect();
    await consumer.subscribe({ topic: TOPIC, fromBeginning: true });
    await consumer.run({
      eachMessage: ({ message }) => {
        received.push(message);
        return Promise.resolve();
      },
    });
    const config = testConfig('http://unused');
    app = await createTestApp({
      ...config,
      auth: NO_AUTH,
      kafka: { bootstrapServers: [kafka.bootstrapServer], changesTopic: TOPIC },
    });
    server = app.getHttpServer();
  });

  afterAll(async () => {
    await app.close();
    await consumer.disconnect();
    await kafka.container.stop();
  });

  it('should_publish_each_change_once_keyed_by_client_id_in_version_order', async () => {
    await request(server).patch('/clients/CLI-70001').send({ status: 'BLOCKED' }).expect(200);
    await request(server).patch('/clients/CLI-70001').send({ status: 'ACTIVE' }).expect(200);
    const database = app.get<Db>(MONGO_DATABASE);
    const outbox = database.collection(OUTBOX_COLLECTION);

    await eventually(() => received.length >= 2);
    await eventually(
      async () => (await outbox.countDocuments({ status: OutboxStatus.PUBLISHED })) === 2,
    );

    const events = received.map((message) => JSON.parse(String(message.value)) as object);
    const validate = createContractValidators().clientChanged;
    expect(received.map((message) => String(message.key))).toEqual(['CLI-70001', 'CLI-70001']);
    expect(events).toEqual([
      expect.objectContaining({ clientId: 'CLI-70001', version: 2, status: 'BLOCKED' }),
      expect.objectContaining({ clientId: 'CLI-70001', version: 3, status: 'ACTIVE' }),
    ]);
    expect(events.every((event) => validate(event))).toBe(true);
    expect(String(received[0]?.headers?.eventId)).toBe((events[0] as { eventId: string }).eventId);
    const metrics = await request(server).get('/metrics').expect(200);
    expect(metrics.text).toContain('outbox_published_total 2');
    expect(metrics.text).toContain('outbox_unpublished 0');
  });
});
