import { errors, JWTVerifyGetKey } from 'jose';

export class IdentityProviderUnavailableError extends Error {
  constructor(cause: unknown) {
    super('Signing keys could not be retrieved from the identity provider', { cause });
    this.name = IdentityProviderUnavailableError.name;
  }
}

export const TOKEN_ERROR_TYPES = Object.freeze([
  errors.JWTClaimValidationFailed,
  errors.JWTExpired,
  errors.JWSSignatureVerificationFailed,
  errors.JWSInvalid,
  errors.JWTInvalid,
  errors.JOSEAlgNotAllowed,
  errors.JOSENotSupported,
  errors.JWKSNoMatchingKey,
  errors.JWKSMultipleMatchingKeys,
]);

export function isTokenError(error: unknown): boolean {
  return TOKEN_ERROR_TYPES.some((type) => error instanceof type);
}

export function guardedKeySource(keys: JWTVerifyGetKey): JWTVerifyGetKey {
  return async (header, token) => {
    try {
      return await keys(header, token);
    } catch (error: unknown) {
      throw isTokenError(error) ? error : new IdentityProviderUnavailableError(error);
    }
  };
}
