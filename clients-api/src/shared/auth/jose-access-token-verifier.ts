import { errors, JWTPayload, JWTVerifyGetKey, jwtVerify } from 'jose';
import { EnabledAuthConfig } from '../../config/app-config';
import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException } from '../errors/problem.exception';
import { AccessTokenVerifier } from './access-token-verifier';

export const ACCEPTED_ALGORITHMS = ['RS256'];
export const REQUIRED_CLAIMS = ['exp'];
export const AUTH_MESSAGES = {
  missingToken: 'A bearer token is required',
  invalidToken: 'The bearer token is invalid or expired',
  missingRole: 'The token does not grant the required role',
  identityProviderUnavailable: 'The identity provider is unavailable',
} as const;

const BEARER_PATTERN = /^Bearer ([\w-]+\.[\w-]+\.[\w-]+)$/;

interface RealmAccessClaims {
  readonly realm_access: { readonly roles: readonly unknown[] };
}

export function extractBearerToken(header: string | undefined): string | undefined {
  return header === undefined ? undefined : BEARER_PATTERN.exec(header)?.[1];
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

function toVerificationProblem(error: unknown): ProblemException {
  if (error instanceof errors.JOSEError && !(error instanceof errors.JWKSTimeout)) {
    return new ProblemException(ErrorCode.UNAUTHORIZED, AUTH_MESSAGES.invalidToken);
  }
  return new ProblemException(
    ErrorCode.SERVICE_UNAVAILABLE,
    AUTH_MESSAGES.identityProviderUnavailable,
  );
}

export class JoseAccessTokenVerifier implements AccessTokenVerifier {
  constructor(
    private readonly settings: EnabledAuthConfig,
    private readonly keys: JWTVerifyGetKey,
  ) {}

  async authenticate(authorizationHeader: string | undefined): Promise<void> {
    const token = extractBearerToken(authorizationHeader);
    if (token === undefined) {
      throw new ProblemException(ErrorCode.UNAUTHORIZED, AUTH_MESSAGES.missingToken);
    }
    const payload = await this.verify(token);
    if (!hasRealmRole(payload, this.settings.requiredRole)) {
      throw new ProblemException(ErrorCode.FORBIDDEN, AUTH_MESSAGES.missingRole);
    }
  }

  private async verify(token: string): Promise<JWTPayload> {
    try {
      const { payload } = await jwtVerify(token, this.keys, {
        issuer: this.settings.issuer,
        algorithms: ACCEPTED_ALGORITHMS,
        requiredClaims: REQUIRED_CLAIMS,
      });
      return payload;
    } catch (error: unknown) {
      throw toVerificationProblem(error);
    }
  }
}
