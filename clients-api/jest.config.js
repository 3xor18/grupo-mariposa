const FULL_COVERAGE = 100;
const CONTAINER_TEST_TIMEOUT_MS = 120_000;

module.exports = {
  rootDir: '.',
  testEnvironment: 'node',
  globalSetup: '<rootDir>/test/support/global-setup.ts',
  globalTeardown: '<rootDir>/test/support/global-teardown.ts',
  testTimeout: CONTAINER_TEST_TIMEOUT_MS,
  moduleFileExtensions: ['ts', 'js', 'json'],
  testRegex: String.raw`(src|test)/.*\.(spec|e2e-spec)\.ts$`,
  transform: { '^.+\.ts$': ['ts-jest', { tsconfig: 'tsconfig.json' }] },
  collectCoverageFrom: ['src/**/*.ts', '!src/**/*.spec.ts', '!src/main.ts'],
  coverageDirectory: 'coverage',
  coverageReporters: ['text', 'text-summary', 'lcov'],
  coverageThreshold: {
    global: {
      lines: FULL_COVERAGE,
      statements: FULL_COVERAGE,
      functions: FULL_COVERAGE,
      branches: FULL_COVERAGE,
    },
  },
};
