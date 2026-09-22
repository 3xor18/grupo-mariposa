import { Module } from '@nestjs/common';
import { createRemoteJWKSet } from 'jose';
import { APP_CONFIG, AppConfig } from '../../config/app-config';
import {
  ACCESS_TOKEN_VERIFIER,
  AccessTokenVerifier,
  DisabledAccessTokenVerifier,
} from './access-token-verifier';
import { guardedKeySource } from './guarded-key-source';
import { JoseAccessTokenVerifier } from './jose-access-token-verifier';

export function createAccessTokenVerifier(config: AppConfig): AccessTokenVerifier {
  if (!config.auth.enabled) {
    return new DisabledAccessTokenVerifier();
  }
  const keys = guardedKeySource(createRemoteJWKSet(new URL(config.auth.jwksUrl)));
  return new JoseAccessTokenVerifier(config.auth, keys);
}

@Module({
  providers: [
    { provide: ACCESS_TOKEN_VERIFIER, useFactory: createAccessTokenVerifier, inject: [APP_CONFIG] },
  ],
  exports: [ACCESS_TOKEN_VERIFIER],
})
export class AuthModule {}
