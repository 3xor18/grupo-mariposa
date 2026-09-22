export class PendingHolds {
  private readonly controller = new AbortController();

  get signal(): AbortSignal {
    return this.controller.signal;
  }

  get cancelled(): boolean {
    return this.controller.signal.aborted;
  }

  cancelAll(): void {
    this.controller.abort();
  }
}
