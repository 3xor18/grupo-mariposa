export const ROUTES = Object.freeze({
  CLIENTS: 'clients',
  HEALTH: 'health',
  LIVENESS: 'live',
  READINESS: 'ready',
  METRICS: 'metrics',
  DOCS: 'docs',
});

const SEPARATOR = '/';

export function absolutePath(...segments: readonly string[]): string {
  return `${SEPARATOR}${segments.join(SEPARATOR)}`;
}
