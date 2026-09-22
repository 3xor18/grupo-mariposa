const defaultBaseUrl = 'http://localhost:8090';
const defaultUsername = 'analyst';

export const environment = {
  baseUrl: process.env.E2E_BASE_URL ?? defaultBaseUrl,
  username: process.env.E2E_USERNAME ?? defaultUsername,
  password: process.env.E2E_PASSWORD ?? '',
  ci: Boolean(process.env.CI),
};

export function requirePassword(): string {
  if (!environment.password) {
    throw new Error('E2E_PASSWORD must be set to the demo user password (DEMO_USER_PASSWORD).');
  }
  return environment.password;
}
