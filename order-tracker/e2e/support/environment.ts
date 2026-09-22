const defaultBaseUrl = 'http://localhost:8090';
const defaultUsername = 'analyst';
const defaultRealm = 'mariposa';

export const environment = {
  baseUrl: process.env.E2E_BASE_URL ?? defaultBaseUrl,
  username: process.env.E2E_USERNAME ?? defaultUsername,
  password: process.env.E2E_PASSWORD ?? '',
  realm: process.env.E2E_REALM ?? defaultRealm,
  ci: Boolean(process.env.CI),
  browserChannel: process.env.E2E_BROWSER_CHANNEL,
};

export function requirePassword(): string {
  if (!environment.password) {
    throw new Error('E2E_PASSWORD must be set to the demo user password (DEMO_USER_PASSWORD).');
  }
  return environment.password;
}
