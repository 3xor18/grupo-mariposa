export const MONGO_SETTINGS = Symbol('MONGO_SETTINGS');
export const MONGO_CLIENT = Symbol('MONGO_CLIENT');
export const MONGO_DATABASE = Symbol('MONGO_DATABASE');
export const DATABASE_HEALTH = Symbol('DATABASE_HEALTH');

export interface DatabaseHealth {
  isHealthy(): Promise<boolean>;
}
