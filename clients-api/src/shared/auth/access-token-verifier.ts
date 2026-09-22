export const ACCESS_TOKEN_VERIFIER = Symbol('ACCESS_TOKEN_VERIFIER');

export interface AccessTokenVerifier {
  authenticate(authorizationHeader: string | undefined): Promise<void>;
}

export class DisabledAccessTokenVerifier implements AccessTokenVerifier {
  authenticate(): Promise<void> {
    return Promise.resolve();
  }
}
