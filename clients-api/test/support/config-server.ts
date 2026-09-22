import { createServer, IncomingHttpHeaders, Server } from 'node:http';
import { AddressInfo } from 'node:net';

export interface StubResponse {
  readonly status: number;
  readonly body?: string;
  readonly hang?: boolean;
}

export interface RecordedRequest {
  readonly url: string;
  readonly headers: IncomingHttpHeaders;
}

export class ConfigServerStub {
  readonly requests: RecordedRequest[] = [];
  private readonly queue: StubResponse[] = [];

  private constructor(
    private readonly server: Server,
    readonly url: string,
  ) {}

  static async start(): Promise<ConfigServerStub> {
    const holder: { stub?: ConfigServerStub } = {};
    const server = createServer((request, response) => {
      holder.stub?.requests.push({ url: request.url ?? '', headers: request.headers });
      const next = holder.stub?.queue.shift() ?? { status: 200, body: '' };
      if (next.hang === true) {
        return;
      }
      response.writeHead(next.status, { 'content-type': 'text/plain' }).end(next.body ?? '');
    });
    await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve));
    const { port } = server.address() as AddressInfo;
    const stub = new ConfigServerStub(server, `http://127.0.0.1:${String(port)}`);
    holder.stub = stub;
    return stub;
  }

  respond(...responses: StubResponse[]): this {
    this.requests.length = 0;
    this.queue.length = 0;
    this.queue.push(...responses);
    return this;
  }

  async stop(): Promise<void> {
    this.server.closeAllConnections();
    await new Promise<void>((resolve) => {
      this.server.close(() => {
        resolve();
      });
    });
  }
}
