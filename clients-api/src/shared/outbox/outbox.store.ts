import { Collection, Db } from 'mongodb';
import { Clock, systemClock } from '../time/clock';
import {
  OUTBOX_COLLECTION,
  OutboxDocument,
  OutboxMessage,
  OutboxStatus,
  toOutboxMessage,
} from './outbox.document';

export const OUTBOX_STORE = Symbol('OUTBOX_STORE');

export interface OutboxStore {
  claim(owner: string, limit: number, leaseMs: number): Promise<OutboxMessage[]>;
  markPublished(owner: string, ids: readonly string[]): Promise<number>;
  release(owner: string, ids: readonly string[], reason: string): Promise<void>;
  countUnpublished(): Promise<number>;
}

const CLAIM_ORDER = Object.freeze({ createdAt: 1 } as const);
const CLAIM_INDEX = Object.freeze({ status: 1, createdAt: 1 } as const);
const LEASE_FIELDS = Object.freeze({ leaseOwner: '', leaseUntil: '' } as const);

export class MongoOutboxStore implements OutboxStore {
  constructor(
    private readonly database: Db,
    private readonly clock: Clock = systemClock,
  ) {}

  private get collection(): Collection<OutboxDocument> {
    return this.database.collection<OutboxDocument>(OUTBOX_COLLECTION);
  }

  async ensureIndexes(): Promise<void> {
    await this.collection.createIndex(CLAIM_INDEX);
  }

  async claim(owner: string, limit: number, leaseMs: number): Promise<OutboxMessage[]> {
    const claimed: OutboxMessage[] = [];
    while (claimed.length < limit) {
      const document = await this.claimNext(owner, leaseMs);
      if (document === null) {
        break;
      }
      claimed.push(toOutboxMessage(document));
    }
    return claimed;
  }

  async markPublished(owner: string, ids: readonly string[]): Promise<number> {
    const result = await this.collection.updateMany(this.ownedBy(owner, ids), {
      $set: { status: OutboxStatus.PUBLISHED, publishedAt: this.clock() },
      $unset: LEASE_FIELDS,
    });
    return result.modifiedCount;
  }

  async release(owner: string, ids: readonly string[], reason: string): Promise<void> {
    await this.collection.updateMany(this.ownedBy(owner, ids), {
      $set: { status: OutboxStatus.PENDING, lastError: reason },
      $unset: LEASE_FIELDS,
    });
  }

  countUnpublished(): Promise<number> {
    return this.collection.countDocuments({ status: { $ne: OutboxStatus.PUBLISHED } });
  }

  private claimNext(owner: string, leaseMs: number): Promise<OutboxDocument | null> {
    const now = this.clock();
    return this.collection.findOneAndUpdate(
      {
        $or: [
          { status: OutboxStatus.PENDING },
          { status: OutboxStatus.IN_FLIGHT, leaseUntil: { $lt: now } },
        ],
      },
      {
        $set: {
          status: OutboxStatus.IN_FLIGHT,
          leaseOwner: owner,
          leaseUntil: new Date(now.getTime() + leaseMs),
        },
        $inc: { attempts: 1 },
      },
      { sort: CLAIM_ORDER, returnDocument: 'after' },
    );
  }

  private ownedBy(
    owner: string,
    ids: readonly string[],
  ): { _id: { $in: string[] }; status: OutboxStatus; leaseOwner: string } {
    return { _id: { $in: [...ids] }, status: OutboxStatus.IN_FLIGHT, leaseOwner: owner };
  }
}
