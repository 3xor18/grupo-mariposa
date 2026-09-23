import {
  Kafka,
  KafkaConfig as KafkaClientConfig,
  logLevel,
  Message,
  Partitioners,
  Producer,
  TopicMessages,
} from 'kafkajs';
import { KafkaConfig } from '../../config/app-config';
import { SERVICE_NAME } from '../constants/logging.constants';
import { OutboxMessage } from './outbox.document';

export const CHANGE_EVENT_PUBLISHER = Symbol('CHANGE_EVENT_PUBLISHER');

export const EVENT_HEADERS = Object.freeze({
  EVENT_ID: 'eventId',
  CONTENT_TYPE: 'contentType',
});
export const JSON_MEDIA_TYPE = 'application/json';
export const KAFKA_NOT_CONFIGURED = 'Kafka bootstrap servers are not configured';
export const KAFKA_PRODUCER_SETTINGS = Object.freeze({ retries: 5, maxInFlightRequests: 1 });

export interface ChangeEventPublisher {
  publish(messages: readonly OutboxMessage[]): Promise<void>;
  close(): Promise<void>;
}

export class UnconfiguredChangeEventPublisher implements ChangeEventPublisher {
  publish(): Promise<void> {
    return Promise.reject(new Error(KAFKA_NOT_CONFIGURED));
  }

  close(): Promise<void> {
    return Promise.resolve();
  }
}

function toKafkaMessage(message: OutboxMessage): Message {
  return {
    key: message.key,
    value: JSON.stringify(message.payload),
    headers: {
      [EVENT_HEADERS.EVENT_ID]: message.id,
      [EVENT_HEADERS.CONTENT_TYPE]: JSON_MEDIA_TYPE,
    },
  };
}

export function groupByTopic(messages: readonly OutboxMessage[]): TopicMessages[] {
  const byTopic = new Map<string, Message[]>();
  for (const message of messages) {
    const bucket = byTopic.get(message.topic) ?? [];
    bucket.push(toKafkaMessage(message));
    byTopic.set(message.topic, bucket);
  }
  return [...byTopic].map(([topic, topicMessages]) => ({ topic, messages: topicMessages }));
}

export class KafkaChangeEventPublisher implements ChangeEventPublisher {
  private connection: Promise<void> | undefined;

  constructor(private readonly producer: Producer) {}

  async publish(messages: readonly OutboxMessage[]): Promise<void> {
    await this.connect();
    await this.producer.sendBatch({ topicMessages: groupByTopic(messages) });
  }

  async close(): Promise<void> {
    if (this.connection !== undefined) {
      this.connection = undefined;
      await this.producer.disconnect();
    }
  }

  private connect(): Promise<void> {
    this.connection ??= this.producer.connect().catch((error: unknown) => {
      this.connection = undefined;
      throw error;
    });
    return this.connection;
  }
}

export function kafkaClientOptions(config: KafkaConfig): KafkaClientConfig {
  return {
    clientId: SERVICE_NAME,
    brokers: [...config.bootstrapServers],
    ssl: config.tlsEnabled,
    logLevel: logLevel.NOTHING,
    retry: { retries: KAFKA_PRODUCER_SETTINGS.retries },
  };
}

export function createKafkaProducer(config: KafkaConfig): Producer {
  return new Kafka(kafkaClientOptions(config)).producer({
    idempotent: true,
    maxInFlightRequests: KAFKA_PRODUCER_SETTINGS.maxInFlightRequests,
    createPartitioner: Partitioners.DefaultPartitioner,
  });
}

export function createChangeEventPublisher(config: KafkaConfig): ChangeEventPublisher {
  return config.bootstrapServers.length === 0
    ? new UnconfiguredChangeEventPublisher()
    : new KafkaChangeEventPublisher(createKafkaProducer(config));
}
