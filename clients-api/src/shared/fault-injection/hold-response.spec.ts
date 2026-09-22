import { Response } from 'express';
import { EventEmitter } from 'node:events';
import { holdResponse, HoldOutcome } from './hold-response';

describe('holdResponse', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  it('should_resolve_elapsed_and_release_listener_when_duration_passes', async () => {
    const response = new EventEmitter();

    const outcome = holdResponse(response as unknown as Response, 1000);
    jest.advanceTimersByTime(1000);

    await expect(outcome).resolves.toBe(HoldOutcome.ELAPSED);
    expect(response.listenerCount('close')).toBe(0);
  });

  it('should_resolve_aborted_immediately_when_client_closes_connection', async () => {
    const response = new EventEmitter();

    const outcome = holdResponse(response as unknown as Response, 1000);
    response.emit('close');

    await expect(outcome).resolves.toBe(HoldOutcome.ABORTED);
    expect(jest.getTimerCount()).toBe(0);
  });
});
