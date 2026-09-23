import { ClientVersionConflictError } from './client-version-conflict.error';
import { ClientStatus } from './client-status.enum';
import { Segment } from './segment.enum';
import { TaxRegime } from './tax-regime.enum';

export const INITIAL_CLIENT_VERSION = 1;

export interface Client {
  readonly id: string;
  readonly name: string;
  readonly status: ClientStatus;
  readonly segment: Segment;
  readonly taxRegime: TaxRegime;
  readonly market: string;
  readonly version: number;
}

export interface ClientChanges {
  readonly status?: ClientStatus;
  readonly segment?: Segment;
  readonly taxRegime?: TaxRegime;
}

export interface VersionPrecondition {
  readonly acceptedVersions: readonly number[];
}

export interface UpdateClientCommand {
  readonly clientId: string;
  readonly changes: ClientChanges;
  readonly precondition?: VersionPrecondition;
}

export type UpdatePlan =
  | { readonly changed: false; readonly client: Client }
  | { readonly changed: true; readonly client: Client; readonly changes: ClientChanges };

function effectiveChanges(current: Client, changes: ClientChanges): ClientChanges {
  return Object.fromEntries(
    Object.entries(changes).filter(
      ([field, value]) => current[field as keyof ClientChanges] !== value,
    ),
  );
}

export function planUpdate(current: Client, command: UpdateClientCommand): UpdatePlan {
  const { precondition } = command;
  if (precondition !== undefined && !precondition.acceptedVersions.includes(current.version)) {
    throw new ClientVersionConflictError(current.id, current.version);
  }
  const changes = effectiveChanges(current, command.changes);
  if (Object.keys(changes).length === 0) {
    return { changed: false, client: current };
  }
  return {
    changed: true,
    client: { ...current, ...changes, version: current.version + 1 },
    changes,
  };
}
