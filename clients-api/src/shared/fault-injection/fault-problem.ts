import { ErrorCode } from '../errors/error-code.enum';
import { ProblemException, ProblemOptions } from '../errors/problem.exception';
import { HTTP_HEADERS } from '../constants/http.constants';
import { FaultType } from './fault-rule';

export type FailureFaultType = Exclude<FaultType, FaultType.TIMEOUT>;

export const INJECTED_FAULT_DETAIL = 'Injected fault';
export const INJECTED_RETRY_AFTER_SECONDS = '1';

const FAULT_ERROR_CODES: Readonly<Record<FailureFaultType, ErrorCode>> = {
  [FaultType.BAD_REQUEST]: ErrorCode.VALIDATION_ERROR,
  [FaultType.TOO_MANY_REQUESTS]: ErrorCode.RATE_LIMITED,
  [FaultType.INTERNAL_ERROR]: ErrorCode.INTERNAL_ERROR,
  [FaultType.BAD_GATEWAY]: ErrorCode.BAD_GATEWAY,
  [FaultType.SERVICE_UNAVAILABLE]: ErrorCode.SERVICE_UNAVAILABLE,
};

function optionsFor(fault: FailureFaultType): ProblemOptions {
  return fault === FaultType.TOO_MANY_REQUESTS
    ? { headers: { [HTTP_HEADERS.RETRY_AFTER]: INJECTED_RETRY_AFTER_SECONDS } }
    : {};
}

export function faultProblem(fault: FailureFaultType): ProblemException {
  return new ProblemException(FAULT_ERROR_CODES[fault], INJECTED_FAULT_DETAIL, optionsFor(fault));
}
