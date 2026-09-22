import { Response } from 'express';

export enum HoldOutcome {
  ELAPSED = 'ELAPSED',
  ABORTED = 'ABORTED',
}

const CLOSE_EVENT = 'close';

export function holdResponse(response: Response, durationMs: number): Promise<HoldOutcome> {
  return new Promise((resolve) => {
    const onClose = (): void => {
      clearTimeout(timer);
      resolve(HoldOutcome.ABORTED);
    };
    const timer = setTimeout(() => {
      response.off(CLOSE_EVENT, onClose);
      resolve(HoldOutcome.ELAPSED);
    }, durationMs);
    response.once(CLOSE_EVENT, onClose);
  });
}
