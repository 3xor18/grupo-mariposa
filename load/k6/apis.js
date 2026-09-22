import http from 'k6/http';
import { check } from 'k6';

const KEYCLOAK_URL = __ENV.KEYCLOAK_URL || 'http://localhost:8180';
const PRODUCTS_URL = __ENV.PRODUCTS_URL || 'http://localhost:8081';
const CLIENTS_URL = __ENV.CLIENTS_URL || 'http://localhost:8082';
const ORDERS_URL = __ENV.ORDERS_URL || 'http://localhost:8080';
const TOKEN_PATH = '/realms/mariposa/protocol/openid-connect/token';
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
    'http_req_duration{scenario:catalog}': ['p(95)<50', 'p(99)<150'],
    'http_req_duration{scenario:orders}': ['p(95)<150', 'p(99)<400'],
  },
};

export function setup() {
  const response = http.post(`${KEYCLOAK_URL}${TOKEN_PATH}`, {
    grant_type: 'password',
    client_id: 'orders-cli',
    username: 'admin',
    password: __ENV.DEMO_PASSWORD,
  });
  return { token: response.json('access_token') };
}

const pick = (list) => list[Math.floor(Math.random() * list.length)];
const auth = (token) => ({ headers: { Authorization: `Bearer ${token}` } });

export function catalog(data) {
  const product = http.get(`${PRODUCTS_URL}/products/${pick(PRODUCTS)}?market=MX`, auth(data.token));
  const client = http.get(`${CLIENTS_URL}/clients/${pick(CLIENTS)}`, auth(data.token));
  check(product, { 'product 200': (r) => r.status === STATUS_OK });
  check(client, { 'client 200': (r) => r.status === STATUS_OK });
}

export function orders(data) {
  const page = http.get(`${ORDERS_URL}/orders?status=APPROVED&size=20`, auth(data.token));
  check(page, { 'orders page 200': (r) => r.status === STATUS_OK });
}
