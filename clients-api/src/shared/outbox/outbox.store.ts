import { Collection, Db, Document, MongoServerError } from 'mongodb';
import { Clock, systemClock } from '../time/clock';
import {
  OUTBOX_COLLECTION,
  OutboxDocument,
  OutboxMessage,
  OutboxStatus,
  toOutboxMessage,
  UNPUBLISHED_STATUSES,
} from './outbox.document';

export const OUTBOX_STORE = Symbol('OUTBOX_STORE');

export interface ReleaseRequest {
  readonly owner: string;
  readonly ids: readonly string[];
  readonly reason: string;
  readonly availableAt: Date;
}

export interface OutboxStore {
  claim(owner: string, limit: number, leaseMs: number): Promise<OutboxMessage[]>;
  markPublished(owner: string, ids: readonly string[]): Promise<number>;
  release(request: ReleaseRequest): Promise<void>;
  countPending(): Promise<number>;
  oldestPendingCreatedAt(): Promise<Date | undefined>;
}

export const OUTBOX_INDEXES = Object.freeze({
  claim: { status: 1, createdAt: 1 },
  keyOrder: { key: 1, version: 1 },
  retention: { publishedAt: 1 },
} as const);

export const RETENTION_INDEX_NAME = 'outbox_retention';
const MONGO_ERROR_CODES = Object.freeze({ indexOptionsConflict: 85, indexKeySpecsConflict: 86 });
const INDEX_OPTIONS_CONFLICT_CODES: ReadonlySet<number> = new Set(Object.values(MONGO_ERROR_CODES));
const LEASE_FIELDS = Object.freeze({ leaseOwner: '', leaseUntil: '' } as const);
const OLDEST_FIRST = Object.freeze({ createdAt: 1 } as const);
const KEY_THEN_VERSION = Object.freeze({ key: 1, version: 1 } as const);
const CREATED_AT_ONLY = Object.freeze({ createdAt: 1, _id: 0 } as const);
const OLDEST_PER_KEY = 'oldest';

export function isIndexOptionsConflict(error: unknown): boolean {
  return error instanceof MongoServerError && INDEX_OPTIONS_CONFLICT_CODES.has(Number(error.code));
}

export class MongoOutboxStore implements OutboxStore {
  constructor(
    private readonly database: Db,
    private readonly clock: Clock = systemClock,
  ) {}

  private get collection(): Collection<OutboxDocument> {
    return this.database.collection<OutboxDocument>(OUTBOX_COLLECTION);
  }

  async ensureIndexes(retentionSeconds: number): Promise<void> {
    await this.collection.createIndex(OUTBOX_INDEXES.claim);
    await this.collection.createIndex(OUTBOX_INDEXES.keyOrder);
    try {
      await this.collection.createIndex(OUTBOX_INDEXES.retention, {
        name: RETENTION_INDEX_NAME,
        expireAfterSeconds: retentionSeconds,
      });
    } catch (error: unknown) {
      if (!isIndexOptionsConflict(error)) {
        throw error;
      }
      await this.database.command({
        collMod: OUTBOX_COLLECTION,
        index: { name: RETENTION_INDEX_NAME, expireAfterSeconds: retentionSeconds },
      });
    }
  }

  async claim(owner: string, limit: number, leaseMs: number): Promise<OutboxMessage[]> {
    const now = this.clock();
    const candidates = await this.collection
      .aggregate<OutboxDocument>(this.candidatePipeline(now, limit))
      .toArray();
    const leased = await Promise.all(
      candidates.map((candidate) => this.lease(candidate._id, owner, now, leaseMs)),
    );
    return leased.filter((document) => document !== null).map(toOutboxMessage);
  }

  async markPublished(owner: string, ids: readonly string[]): Promise<number> {
    const result = await this.collection.updateMany(this.ownedBy(owner, ids), {
      $set: { status: OutboxStatus.PUBLISHED, publishedAt: this.clock() },
      $unset: LEASE_FIELDS,
    });
    return result.modifiedCount;
  }

  async release(request: ReleaseRequest): Promise<void> {
    await this.collection.updateMany(this.ownedBy(request.owner, request.ids), {
      $set: {
        status: OutboxStatus.PENDING,
        availableAt: request.availableAt,
        lastError: request.reason,
      },
      $inc: { attempts: 1 },
      $unset: LEASE_FIELDS,
    });
  }

  countPending(): Promise<number> {
    return this.collection.countDocuments({ status: { $in: [...UNPUBLISHED_STATUSES] } });
  }

  async oldestPendingCreatedAt(): Promise<Date | undefined> {
    const oldest = await this.collection.findOne(
      { status: { $in: [...UNPUBLISHED_STATUSES] } },
      { sort: OLDEST_FIRST, projection: CREATED_AT_ONLY },
    );
    return oldest?.createdAt;
  }

  private candidatePipeline(now: Date, limit: number): Document[] {
    return [
      { $match: { status: { $in: [...UNPUBLISHED_STATUSES] } } },
      { $sort: KEY_THEN_VERSION },
      { $group: { _id: '$key', [OLDEST_PER_KEY]: { $first: '$$ROOT' } } },
      { $replaceWith: `$${OLDEST_PER_KEY}` },
      { $match: this.claimable(now) },
      { $sort: OLDEST_FIRST },
      { $limit: limit },
    ];
  }

  private lease(
    id: string,
    owner: string,
    now: Date,
    leaseMs: number,
  ): Promise<OutboxDocument | null> {
    return this.collection.findOneAndUpdate(
      { _id: id, ...this.claimable(now) },
      {
        $set: {
          status: OutboxStatus.IN_FLIGHT,
          leaseOwner: owner,
          leaseUntil: new Date(now.getTime() + leaseMs),
        },
      },
      { returnDocument: 'after' },
    );
  }

  private claimable(now: Date): Document {
    return {
      $or: [
        { status: OutboxStatus.PENDING, availableAt: { $lte: now } },
        { status: OutboxStatus.IN_FLIGHT, leaseUntil: { $lt: now } },
      ],
    };
  }

  private ownedBy(
    owner: string,
    ids: readonly string[],
  ): { _id: { $in: string[] }; status: OutboxStatus; leaseOwner: string } {
    return { _id: { $in: [...ids] }, status: OutboxStatus.IN_FLIGHT, leaseOwner: owner };
  }
}
