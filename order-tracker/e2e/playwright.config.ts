import { defineConfig, devices } from '@playwright/test';
import { environment } from './support/environment';

export default defineConfig({
  testDir: './tests',
  timeout: 90_000,
  expect: { timeout: 20_000 },
  fullyParallel: false,
  workers: 1,
  forbidOnly: environment.ci,
  retries: 0,
  reporter: environment.ci ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: environment.baseUrl,
    locale: 'es-MX',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    ...(environment.browserChannel ? { channel: environment.browserChannel } : {}),
  },
  projects: [
    { name: 'desktop-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile-chromium', use: { ...devices['Pixel 7'] } },
  ],
});
