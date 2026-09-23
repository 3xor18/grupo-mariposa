import { Producer } from 'kafkajs';
import {
  createChangeEventPublisher,
  createKafkaProducer,
  groupByTopic,
  KAFKA_NOT_CONFIGURED,
  KafkaChangeEventPublisher,
  UnconfiguredChangeEventPublisher,
} from './change-event-publisher';

const MESSAGE = { id: 'event-1', topic: 'clients.changed.v1', key: 'CLI-1', payload: { a: 1 } };

function producerStub(): {
  connect: jest.Mock;
  disconnect: jest.Mock;
  sendBatch: jest.Mock;
} {
  return {
    connect: jest.fn().mockResolvedValue(undefined),
    disconnect: jest.fn().mockResolvedValue(undefined),
    sendBatch: jest.fn().mockResolvedValue([]),
  };
}

const kafkaMessage = (eventId: string): object => ({
  key: 'CLI-1',
  value: '{"a":1}',
  headers: { eventId, contentType: 'application/json' },
});

describe('groupByTopic', () => {
  it('should_group_messages_per_topic_keeping_order_key_and_headers', () => {
    const other = { ...MESSAGE, id: 'event-3', topic: 'other' };

    expect(groupByTopic([MESSAGE, other, { ...MESSAGE, id: 'event-2' }])).toEqual([
      { topic: 'clients.changed.v1', messages: [kafkaMessage('event-1'), kafkaMessage('event-2')] },
      { topic: 'other', messages: [kafkaMessage('event-3')] },
    ]);
  });
});

describe('KafkaChangeEventPublisher', () => {
  it('should_connect_once_and_send_batches', async () => {
    const producer = producerStub();
    const publisher = new KafkaChangeEventPublisher(producer as unknown as Producer);

    await publisher.publish([MESSAGE]);
    await publisher.publish([MESSAGE]);

    expect(producer.connect).toHaveBeenCalledTimes(1);
    expect(producer.sendBatch).toHaveBeenCalledTimes(2);
  });

  it('should_retry_the_connection_after_a_failed_attempt', async () => {
    const producer = producerStub();
    producer.connect.mockRejectedValueOnce(new Error('broker down'));
    const publisher = new KafkaChangeEventPublisher(producer as unknown as Producer);

    await expect(publisher.publish([MESSAGE])).rejects.toThrow('broker down');
    await publisher.publish([MESSAGE]);

    expect(producer.connect).toHaveBeenCalledTimes(2);
    expect(producer.sendBatch).toHaveBeenCalledTimes(1);
  });

  it('should_disconnect_only_when_connected', async () => {
    const producer = producerStub();
    const publisher = new KafkaChangeEventPublisher(producer as unknown as Producer);

    await publisher.close();
    expect(producer.disconnect).not.toHaveBeenCalled();

    await publisher.publish([MESSAGE]);
    await publisher.close();
    await publisher.close();
    expect(producer.disconnect).toHaveBeenCalledTimes(1);
  });
});

describe('createChangeEventPublisher', () => {
  it('should_refuse_to_publish_when_kafka_is_not_configured', async () => {
    const publisher = createChangeEventPublisher({ bootstrapServers: [], changesTopic: 't' });

    expect(publisher).toBeInstanceOf(UnconfiguredChangeEventPublisher);
    await expect(publisher.publish([])).rejects.toThrow(KAFKA_NOT_CONFIGURED);
    await expect(publisher.close()).resolves.toBeUndefined();
  });

  it('should_build_an_idempotent_kafka_publisher_when_brokers_are_set', () => {
    const config = { bootstrapServers: ['localhost:9092'], changesTopic: 't' };

    expect(createChangeEventPublisher(config)).toBeInstanceOf(KafkaChangeEventPublisher);
    expect(typeof createKafkaProducer(config).sendBatch).toBe('function');
  });
});
