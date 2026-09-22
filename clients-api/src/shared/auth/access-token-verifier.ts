import { Principal } from './principal';

export const ACCESS_TOKEN_VERIFIER = Symbol('ACCESS_TOKEN_VERIFIER');

export interface AccessTokenVerifier {
  authenticate(authorizationHeader: string | undefined): Promise<Principal | undefined>;
}

export class DisabledAccessTokenVerifier implements AccessTokenVerifier {
  authenticate(): Promise<Principal | undefined> {
    return Promise.resolve(undefined);
  }
}
