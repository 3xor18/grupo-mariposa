import { createServer, Server } from 'node:http';
import { AddressInfo } from 'node:net';
import { exportJWK, generateKeyPair, JWTPayload, KeyLike, SignJWT } from 'jose';

export const TEST_ISSUER = 'http://localhost:8180/realms/mariposa';
export const TEST_ROLE = 'clients-reader';
const KEY_ID = 'test-key';

export interface TokenOptions {
  readonly roles?: readonly string[];
  readonly issuer?: string;
  readonly expiresIn?: string;
  readonly expired?: boolean;
  readonly key?: KeyLike;
  readonly claims?: JWTPayload;
}

export class TestIdentityProvider {
  private constructor(
    private readonly server: Server,
    private readonly privateKey: KeyLike,
    readonly jwksUrl: string,
  ) {}

  static async start(): Promise<TestIdentityProvider> {
    const { publicKey, privateKey } = await generateKeyPair('RS256');
    const jwk = { ...(await exportJWK(publicKey)), kid: KEY_ID, alg: 'RS256', use: 'sig' };
    const body = JSON.stringify({ keys: [jwk] });
    const server = createServer((_request, response) => {
      response.writeHead(200, { 'content-type': 'application/json' }).end(body);
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const { port } = server.address() as AddressInfo;
    return new TestIdentityProvider(server, privateKey, `http://127.0.0.1:${String(port)}/certs`);
  }

  static async foreignKey(): Promise<KeyLike> {
    return (await generateKeyPair('RS256')).privateKey;
  }

  async token(options: TokenOptions = {}): Promise<string> {
    const nowSeconds = Math.floor(Date.now() / 1000);
    const jwt = new SignJWT({
      realm_access: { roles: options.roles ?? [TEST_ROLE] },
      ...options.claims,
    })
      .setProtectedHeader({ alg: 'RS256', kid: KEY_ID })
      .setIssuer(options.issuer ?? TEST_ISSUER)
      .setSubject('service-account-order-processor')
      .setIssuedAt(nowSeconds - 120);
    if (options.expired === true) {
      jwt.setExpirationTime(nowSeconds - 60);
    } else {
      jwt.setExpirationTime(options.expiresIn ?? '5m');
    }
    return jwt.sign(options.key ?? this.privateKey);
  }

  async stop(): Promise<void> {
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
