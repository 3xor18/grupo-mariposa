export class RequestAbortedError extends Error {
  constructor() {
    super('Client closed the request before the response was sent');
    this.name = RequestAbortedError.name;
  }
}
