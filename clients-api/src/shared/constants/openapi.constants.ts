export const OPERATIONS = Object.freeze({
  getClient: Object.freeze({ operationId: 'getClient', summary: 'Get a client by id' }),
  updateClient: Object.freeze({
    operationId: 'updateClient',
    summary: 'Update a client (admin); publishes clients.changed.v1',
  }),
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
  clientUpdated: 'Updated; ETag carries the new version',
  health: 'Health status',
  ifMatch: 'Expected current version, e.g. "3". When omitted the update is unconditional.',
  etag: 'Current version of the client',
});
