import { StartedMongoDBContainer } from '@testcontainers/mongodb';

export default async function globalTeardown(): Promise<void> {
  const { mongoContainer } = globalThis as { mongoContainer?: StartedMongoDBContainer };
  await mongoContainer?.stop();
}
