import { Response } from 'express';
import { RESPONSE_EVENTS } from '../constants/http.constants';

export enum HoldOutcome {
  ELAPSED = 'ELAPSED',
  ABORTED = 'ABORTED',
  CANCELLED = 'CANCELLED',
}

const ABORT_EVENT = 'abort';

export function holdResponse(
  response: Response,
  durationMs: number,
  cancellation: AbortSignal,
): Promise<HoldOutcome> {
  return new Promise((resolve) => {
    const finish = (outcome: HoldOutcome): void => {
      clearTimeout(timer);
      response.off(RESPONSE_EVENTS.CLOSE, onClose);
      cancellation.removeEventListener(ABORT_EVENT, onCancel);
      resolve(outcome);
    };
    const onClose = (): void => {
      finish(HoldOutcome.ABORTED);
    };
    const onCancel = (): void => {
      finish(HoldOutcome.CANCELLED);
    };
    const timer = setTimeout(() => {
      finish(HoldOutcome.ELAPSED);
    }, durationMs);
    response.once(RESPONSE_EVENTS.CLOSE, onClose);
    cancellation.addEventListener(ABORT_EVENT, onCancel, { once: true });
  });
}
