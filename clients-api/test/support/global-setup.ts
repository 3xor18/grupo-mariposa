import { MONGO_URI_VARIABLE, mongoUriOf, startMongo } from './containers';

export default async function globalSetup(): Promise<void> {
  const mongo = await startMongo();
  process.env[MONGO_URI_VARIABLE] = mongoUriOf(mongo);
  Object.assign(globalThis, { mongoContainer: mongo });
}
