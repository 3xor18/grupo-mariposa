import { JWTPayload, JWTVerifyGetKey, JWTVerifyOptions, jwtVerify } from 'jose';
import { EnabledAuthConfig } from '../../config/app-config';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { AccessTokenVerifier } from './access-token-verifier';
import { IdentityProviderUnavailableError, isTokenError } from './guarded-key-source';
import { Principal } from './principal';

export const ACCEPTED_ALGORITHMS: readonly string[] = Object.freeze(['RS256']);
export const REQUIRED_CLAIMS: readonly string[] = Object.freeze(['exp']);
export const AUTH_MESSAGES = Object.freeze({
  missingToken: 'A bearer token is required',
  malformedToken: 'The authorization header must be "Bearer <jwt>"',
  invalidToken: 'The bearer token is invalid or expired',
  missingRole: 'The token does not grant the required role',
  identityProviderUnavailable: 'The identity provider is unavailable',
});

const BEARER_PATTERN = /^bearer +([\w-]+\.[\w-]+\.[\w-]*)$/i;

interface RealmAccessClaims {
  readonly realm_access: { readonly roles: readonly unknown[] };
}

export function extractBearerToken(header: string | undefined): string {
  if (header === undefined || header.length === 0) {
    throw new ProblemException(ErrorCode.UNAUTHORIZED, AUTH_MESSAGES.missingToken);
  }
  const token = BEARER_PATTERN.exec(header)?.[1];
  if (token === undefined) {
    throw new ProblemException(ErrorCode.UNAUTHORIZED, AUTH_MESSAGES.malformedToken);
  }
  return token;
}

function hasRealmRoles(payload: JWTPayload): payload is JWTPayload & RealmAccessClaims {
  const realmAccess = payload.realm_access;
  return (
    typeof realmAccess === 'object' &&
    realmAccess !== null &&
    'roles' in realmAccess &&
    Array.isArray(realmAccess.roles)
  );
}

export function hasRealmRole(payload: JWTPayload, role: string): boolean {
  return hasRealmRoles(payload) && payload.realm_access.roles.includes(role);
}

export function principalOf(payload: JWTPayload): Principal {
  const authorizedParty = payload.azp;
  const id = typeof authorizedParty === 'string' ? authorizedParty : payload.sub;
  if (id === undefined || id.length === 0) {
    throw new ProblemException(ErrorCode.UNAUTHORIZED, AUTH_MESSAGES.invalidToken);
  }
  return { id };
}

export function toVerificationError(error: unknown): unknown {
  if (error instanceof IdentityProviderUnavailableError) {
    return new ProblemException(
      ErrorCode.SERVICE_UNAVAILABLE,
      AUTH_MESSAGES.identityProviderUnavailable,
      { cause: error },
    );
  }
  if (isTokenError(error)) {
    return new ProblemException(ErrorCode.UNAUTHORIZED, AUTH_MESSAGES.invalidToken);
  }
  return error;
}

export class JoseAccessTokenVerifier implements AccessTokenVerifier {
  private readonly options: JWTVerifyOptions;

  constructor(
    private readonly settings: EnabledAuthConfig,
    private readonly keys: JWTVerifyGetKey,
  ) {
    this.options = {
      issuer: settings.issuer,
      algorithms: [...ACCEPTED_ALGORITHMS],
      requiredClaims: [...REQUIRED_CLAIMS],
      audience: settings.audience,
    };
  }

  async authenticate(authorizationHeader: string | undefined): Promise<Principal> {
    const payload = await this.verify(extractBearerToken(authorizationHeader));
    if (!hasRealmRole(payload, this.settings.requiredRole)) {
      throw new ProblemException(ErrorCode.FORBIDDEN, AUTH_MESSAGES.missingRole);
    }
    return principalOf(payload);
  }

  private async verify(token: string): Promise<JWTPayload> {
    try {
      const { payload } = await jwtVerify(token, this.keys, this.options);
      return payload;
    } catch (error: unknown) {
      throw toVerificationError(error);
    }
  }
}
