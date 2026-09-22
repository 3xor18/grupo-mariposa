import { Response } from 'express';
import { EventEmitter } from 'node:events';
import { holdResponse, HoldOutcome } from './hold-response';
import { PendingHolds } from './pending-holds';

describe('holdResponse', () => {
  beforeEach(() => {
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  const hold = (response: EventEmitter, holds: PendingHolds): Promise<HoldOutcome> =>
    holdResponse(response as unknown as Response, 1000, holds.signal);

  it('should_resolve_elapsed_and_release_listeners_when_duration_passes', async () => {
    const response = new EventEmitter();

    const outcome = hold(response, new PendingHolds());
    jest.advanceTimersByTime(1000);

    await expect(outcome).resolves.toBe(HoldOutcome.ELAPSED);
    expect(response.listenerCount('close')).toBe(0);
  });

  it('should_resolve_aborted_immediately_when_client_closes_connection', async () => {
    const response = new EventEmitter();

    const outcome = hold(response, new PendingHolds());
    response.emit('close');

    await expect(outcome).resolves.toBe(HoldOutcome.ABORTED);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('should_resolve_cancelled_for_every_pending_hold_when_shutdown_cancels', async () => {
    const holds = new PendingHolds();
    const first = new EventEmitter();
    const second = new EventEmitter();

    const outcomes = Promise.all([hold(first, holds), hold(second, holds)]);
    holds.cancelAll();

    await expect(outcomes).resolves.toEqual([HoldOutcome.CANCELLED, HoldOutcome.CANCELLED]);
    expect(holds.cancelled).toBe(true);
    expect(first.listenerCount('close')).toBe(0);
    expect(jest.getTimerCount()).toBe(0);
  });
});
