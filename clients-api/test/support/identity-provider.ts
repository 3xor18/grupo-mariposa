import { createServer, Server } from 'node:http';
import { AddressInfo } from 'node:net';
import {
  base64url,
  exportJWK,
  exportSPKI,
  generateKeyPair,
  JWTPayload,
  KeyLike,
  SignJWT,
} from 'jose';

export const TEST_ISSUER = 'http://localhost:8180/realms/mariposa';
export const TEST_ROLE = 'clients-reader';
export const TEST_AUDIENCE = 'clients-api';
export const TEST_CLIENT = 'order-processor';
const KEY_ID = 'test-key';

export interface TokenOptions {
  readonly roles?: readonly string[];
  readonly issuer?: string;
  readonly audience?: string | string[];
  readonly withoutAudience?: boolean;
  readonly expiresIn?: string;
  readonly expired?: boolean;
  readonly withoutExpiration?: boolean;
  readonly notBefore?: string;
  readonly keyId?: string;
  readonly key?: KeyLike;
  readonly claims?: JWTPayload;
}

export type JwksBehaviour = 'ok' | 'error' | 'hang';

export class TestIdentityProvider {
  behaviour: JwksBehaviour = 'ok';

  private constructor(
    private readonly server: Server,
    private readonly privateKey: KeyLike,
    readonly publicKeyPem: string,
    readonly jwksUrl: string,
  ) {}

  static async start(): Promise<TestIdentityProvider> {
    const { publicKey, privateKey } = await generateKeyPair('RS256');
    const jwk = { ...(await exportJWK(publicKey)), kid: KEY_ID, alg: 'RS256', use: 'sig' };
    const body = JSON.stringify({ keys: [jwk] });
    const holder: { idp?: TestIdentityProvider } = {};
    const server = createServer((_request, response) => {
      const behaviour = holder.idp?.behaviour ?? 'ok';
      if (behaviour === 'hang') {
        return;
      }
      const status = behaviour === 'ok' ? 200 : 500;
      response.writeHead(status, { 'content-type': 'application/json' }).end(body);
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const { port } = server.address() as AddressInfo;
    const url = `http://127.0.0.1:${String(port)}/certs`;
    holder.idp = new TestIdentityProvider(server, privateKey, await exportSPKI(publicKey), url);
    return holder.idp;
  }

  static async foreignKey(): Promise<KeyLike> {
    return (await generateKeyPair('RS256')).privateKey;
  }

  static unsignedToken(payload: JWTPayload): string {
    const encode = (value: object): string => base64url.encode(JSON.stringify(value));
    return `${encode({ alg: 'none', typ: 'JWT' })}.${encode(payload)}.`;
  }

  async token(options: TokenOptions = {}): Promise<string> {
    const nowSeconds = Math.floor(Date.now() / 1000);
    const jwt = new SignJWT({
      realm_access: { roles: options.roles ?? [TEST_ROLE] },
      azp: TEST_CLIENT,
      ...options.claims,
    })
      .setProtectedHeader({ alg: 'RS256', kid: options.keyId ?? KEY_ID })
      .setIssuer(options.issuer ?? TEST_ISSUER)
      .setSubject('service-account-order-processor')
      .setIssuedAt(nowSeconds - 120);
    if (options.withoutAudience !== true) {
      jwt.setAudience(options.audience ?? TEST_AUDIENCE);
    }
    if (options.notBefore !== undefined) {
      jwt.setNotBefore(options.notBefore);
    }
    if (options.expired === true) {
      jwt.setExpirationTime(nowSeconds - 60);
    } else if (options.withoutExpiration !== true) {
      jwt.setExpirationTime(options.expiresIn ?? '5m');
    }
    return jwt.sign(options.key ?? this.privateKey);
  }

  async stop(): Promise<void> {
    this.server.closeAllConnections();
    await new Promise<void>((resolve, reject) => {
      this.server.close((error) => {
        if (error) {
          reject(error);
        } else {
          resolve();
        }
      });
    });
  }
}
