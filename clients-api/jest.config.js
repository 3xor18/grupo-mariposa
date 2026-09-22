const FULL_COVERAGE = 100;

module.exports = {
  rootDir: '.',
  testEnvironment: 'node',
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
    },
  },
};
