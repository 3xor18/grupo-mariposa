export const OUTBOX_COLLECTION = 'outbox';

export enum OutboxStatus {
  PENDING = 'PENDING',
  IN_FLIGHT = 'IN_FLIGHT',
  PUBLISHED = 'PUBLISHED',
}

export const UNPUBLISHED_STATUSES: readonly OutboxStatus[] = Object.freeze([
  OutboxStatus.PENDING,
  OutboxStatus.IN_FLIGHT,
]);

export interface OutboxMessage {
  readonly id: string;
  readonly topic: string;
  readonly key: string;
  readonly version: number;
  readonly payload: object;
}

export interface OutboxDocument {
  readonly _id: string;
  readonly topic: string;
  readonly key: string;
  readonly version: number;
  readonly payload: object;
  readonly status: OutboxStatus;
  readonly attempts: number;
  readonly createdAt: Date;
  readonly availableAt: Date;
  readonly leaseOwner?: string;
  readonly leaseUntil?: Date;
  readonly publishedAt?: Date;
  readonly lastError?: string;
}

export function pendingOutboxDocument(message: OutboxMessage, createdAt: Date): OutboxDocument {
  return {
    _id: message.id,
    topic: message.topic,
    key: message.key,
    version: message.version,
    payload: message.payload,
    status: OutboxStatus.PENDING,
    attempts: 0,
    createdAt,
    availableAt: createdAt,
  };
}

export function toOutboxMessage(document: OutboxDocument): OutboxMessage {
  return {
    id: document._id,
    topic: document.topic,
    key: document.key,
    version: document.version,
    payload: document.payload,
  };
}
