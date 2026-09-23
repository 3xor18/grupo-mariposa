export const OPERATIONS = Object.freeze({
  getClient: Object.freeze({ operationId: 'getClient', summary: 'Get a client by id' }),
  liveness: Object.freeze({ operationId: 'liveness', summary: 'Liveness probe' }),
  readiness: Object.freeze({ operationId: 'readiness', summary: 'Readiness probe' }),
});

export const OPENAPI_TYPES = Object.freeze({
  STRING: 'string',
  HTTP_SECURITY: 'http',
  BEARER_SCHEME: 'bearer',
});

export const RESPONSE_DESCRIPTIONS = Object.freeze({
  clientFound: 'Client found',
  health: 'Health status',
});
