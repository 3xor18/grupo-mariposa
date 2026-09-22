import http from 'k6/http';
import { check, fail } from 'k6';

const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8180';
const PRODUCTS_URL = __ENV.PRODUCTS_URL || 'http://localhost:8081';
const CLIENTS_URL = __ENV.CLIENTS_URL || 'http://localhost:8082';
const ORDERS_URL = __ENV.ORDERS_URL || 'http://localhost:8080';
const TOKEN_PATH = '/realms/mariposa/protocol/openid-connect/token';
const TOKEN_CLIENT_ID = 'orders-cli';
const TOKEN_USER = 'admin';
const TOKEN_REFRESH_MARGIN_MS = 30000;
const MILLIS_PER_SECOND = 1000;
const PRODUCTS = ['PRD-001', 'PRD-002', 'PRD-003', 'PRD-008'];
const CLIENTS = ['CLI-99821', 'CLI-10002', 'CLI-10003'];
const STATUS_OK = 200;

export const options = {
  scenarios: {
    catalog: { executor: 'constant-arrival-rate', rate: 100, timeUnit: '1s', duration: '60s',
      preAllocatedVUs: 20, exec: 'catalog' },
    orders: { executor: 'constant-arrival-rate', rate: 20, timeUnit: '1s', duration: '60s',
      preAllocatedVUs: 10, exec: 'orders' },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    checks: ['rate>0.99'],
    'http_req_duration{scenario:catalog,endpoint:api}': ['p(95)<50', 'p(99)<150'],
    'http_req_duration{scenario:orders,endpoint:api}': ['p(95)<150', 'p(99)<400'],
  },
};

let session = null;

function requestToken(form) {
  const response = http.post(`${KEYCLOAK_URL}${TOKEN_PATH}`, form, { tags: { endpoint: 'token' } });
  const ok = check(response, {
    'token 200': (r) => r.status === STATUS_OK,
    'token present': (r) => Boolean(r.json('access_token')),
  });
  if (!ok) {
    fail(`token request failed with status ${response.status}`);
  }
  return {
    accessToken: response.json('access_token'),
    refreshToken: response.json('refresh_token'),
    expiresAt: Date.now() + response.json('expires_in') * MILLIS_PER_SECOND,
  };
}

function passwordGrant() {
  if (!__ENV.DEMO_PASSWORD) {
    fail('DEMO_PASSWORD is required');
  }
  return requestToken({
    grant_type: 'password',
    client_id: TOKEN_CLIENT_ID,
    username: TOKEN_USER,
    password: __ENV.DEMO_PASSWORD,
  });
}

function refreshGrant(refreshToken) {
  return requestToken({
    grant_type: 'refresh_token',
    client_id: TOKEN_CLIENT_ID,
    refresh_token: refreshToken,
  });
}

function accessToken() {
  if (session === null) {
    session = passwordGrant();
  } else if (Date.now() > session.expiresAt - TOKEN_REFRESH_MARGIN_MS) {
    session = session.refreshToken ? refreshGrant(session.refreshToken) : passwordGrant();
  }
  return session.accessToken;
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const auth = () => ({
  headers: { Authorization: `Bearer ${accessToken()}` },
  tags: { endpoint: 'api' },
});

export function catalog() {
  const product = http.get(`${PRODUCTS_URL}/products/${pick(PRODUCTS)}?market=MX`, auth());
  const client = http.get(`${CLIENTS_URL}/clients/${pick(CLIENTS)}`, auth());
  check(product, { 'product 200': (r) => r.status === STATUS_OK });
  check(client, { 'client 200': (r) => r.status === STATUS_OK });
}

export function orders() {
  const page = http.get(`${ORDERS_URL}/orders?status=APPROVED&size=20`, auth());
  check(page, { 'orders page 200': (r) => r.status === STATUS_OK });
}
