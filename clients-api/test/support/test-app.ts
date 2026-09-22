import { NestExpressApplication } from '@nestjs/platform-express';
import { Test } from '@nestjs/testing';
import { AppModule } from '../../src/app.module';
import { configureApp } from '../../src/app.setup';
import { AppConfig, LogLevel } from '../../src/config/app-config';
import { parseFaultRules } from '../../src/shared/fault-injection/fault-rule';
import { TEST_AUDIENCE, TEST_ISSUER, TEST_ROLE } from './identity-provider';

export const DEFAULT_FAULT_RULES =
  'CLI-40001:503:2,CLI-40002:503,CLI-FAULT400:400,CLI-FAULT429:429,' +
  'CLI-FAULT500:500,CLI-FAULT502:502,CLI-SLOW:timeout';

export const NO_AUTH = Object.freeze({ enabled: false } as const);

export function testConfig(jwksUrl: string, overrides: Partial<AppConfig> = {}): AppConfig {
  return {
    port: 0,
    logLevel: LogLevel.SILENT,
    auth: {
      enabled: true,
      issuer: TEST_ISSUER,
      jwksUrl,
      requiredRole: TEST_ROLE,
      audience: TEST_AUDIENCE,
    },
    faultInjection: {
      enabled: true,
      rules: parseFaultRules(DEFAULT_FAULT_RULES),
      timeoutMs: 100,
    },
    rateLimit: { requestsPerSecond: 1000, burst: 1000, maxTrackedCallers: 100 },
    shutdown: { drainMs: 0, timeoutMs: 1000 },
    http: { apiDocsEnabled: true, trustProxy: false },
    ...overrides,
  };
}

export interface ProviderOverride {
  readonly token: string | symbol;
  readonly value: unknown;
}

export async function createTestApp(
  config: AppConfig,
  override?: ProviderOverride,
): Promise<NestExpressApplication> {
  const builder = Test.createTestingModule({ imports: [AppModule.forRoot(config)] });
  if (override !== undefined) {
    builder.overrideProvider(override.token).useValue(override.value);
  }
  const moduleRef = await builder.compile();
  const app = moduleRef.createNestApplication<NestExpressApplication>({
    bufferLogs: true,
    forceCloseConnections: true,
  });
  configureApp(app);
  await app.init();
  return app;
}
