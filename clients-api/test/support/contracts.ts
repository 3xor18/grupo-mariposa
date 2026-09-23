import Ajv2020, { ValidateFunction } from 'ajv/dist/2020';
import addFormats from 'ajv-formats';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parse } from 'yaml';

export const CONTRACTS_DIR = join(__dirname, '..', '..', '..', 'contracts');

export interface OpenApiOperation {
  readonly operationId: string;
  readonly responses: Record<string, unknown>;
  readonly security?: unknown[];
}

export interface OpenApiContract {
  readonly openapi: string;
  readonly paths: Record<string, Record<string, OpenApiOperation>>;
  readonly components: {
    readonly schemas: Record<string, object>;
    readonly responses: Record<string, { content: Record<string, { schema: object }> }>;
  };
}

export function loadClientsContract(): OpenApiContract {
  const raw = readFileSync(join(CONTRACTS_DIR, 'http', 'clients-api.openapi.yaml'), 'utf8');
  return parse(raw) as OpenApiContract;
}

function loadProblemSchema(): object {
  const raw = readFileSync(join(CONTRACTS_DIR, 'common', 'problem.schema.json'), 'utf8');
  return JSON.parse(raw) as object;
}

export interface ContractValidators {
  readonly problem: ValidateFunction;
  readonly client: ValidateFunction;
  readonly health: ValidateFunction;
}

export function createContractValidators(): ContractValidators {
  const ajv = new Ajv2020({ allErrors: true, strict: false });
  addFormats(ajv);
  const contract = loadClientsContract();
  const healthSchema = contract.components.responses.Health?.content['application/json']?.schema;
  return {
    problem: ajv.compile(loadProblemSchema()),
    client: ajv.compile({ ...contract.components.schemas.Client, additionalProperties: false }),
    health: ajv.compile(healthSchema ?? {}),
  };
}
