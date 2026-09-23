import { setTimeout as delay } from 'node:timers/promises';
import { ConfigSource } from '../load-config';
import { BootstrapLogger, createBootstrapLogger } from './bootstrap-logger';
import { ConfigServerTransport, fetchRemoteProperties } from './config-server-client';
import {
  ConfigServerSettings,
  loadConfigServerSettings,
  propertiesUrlOf,
} from './config-server-settings';
import { Properties, toEnvironmentStyle } from './properties';

export const REMOTE_CONFIG_MESSAGES = {
  loaded: 'Configuration loaded from config server',
  unavailable: 'Config server unavailable, continuing with environment and defaults',
} as const;

export interface RemoteConfigDependencies extends ConfigServerTransport {
  readonly logger?: BootstrapLogger;
}

export const defaultTransport: ConfigServerTransport = {
  fetch: (input, init) => fetch(input, init),
  sleep: async (milliseconds) => {
    await delay(milliseconds);
  },
  random: Math.random,
};

function definedEntriesOf(environment: ConfigSource): Properties {
  return Object.fromEntries(
    Object.entries(environment).filter(
      (entry): entry is [string, string] => entry[1] !== undefined,
    ),
  );
}

function withEnvironmentPrecedence(remote: Properties, environment: ConfigSource): ConfigSource {
  return { ...remote, ...definedEntriesOf(environment) };
}

async function fetchOrFallback(
  settings: ConfigServerSettings,
  transport: ConfigServerTransport,
  logger: BootstrapLogger,
): Promise<Properties> {
  try {
    const remote = toEnvironmentStyle(await fetchRemoteProperties(settings, transport));
    logger.info(
      { url: propertiesUrlOf(settings), keys: Object.keys(remote) },
      REMOTE_CONFIG_MESSAGES.loaded,
    );
    return remote;
  } catch (error: unknown) {
    if (settings.failFast) {
      throw error;
    }
    logger.warn({ reason: String(error) }, REMOTE_CONFIG_MESSAGES.unavailable);
    return {};
  }
}

export async function loadRemoteConfig(
  environment: ConfigSource,
  dependencies: RemoteConfigDependencies = defaultTransport,
): Promise<ConfigSource> {
  const settings = loadConfigServerSettings(environment);
  if (settings === undefined) {
    return environment;
  }
  const logger = dependencies.logger ?? createBootstrapLogger(settings.logLevel);
  const remote = await fetchOrFallback(settings, dependencies, logger);
  return withEnvironmentPrecedence(remote, environment);
}
