import { ErrorCode } from './error-code.enum';
import { buildProblemDetails, problemTypeFor } from './problem-details';

describe('problem details', () => {
  const context = {
    instance: '/clients/CLI-1',
    traceId: 'trace-1',
    timestamp: new Date('2026-09-22T10:00:00.000Z'),
  };
  const descriptor = {
    status: 404,
    code: ErrorCode.CLIENT_NOT_FOUND,
    title: 'Client not found',
    detail: 'Client CLI-1 does not exist',
    unexpected: false,
  };

  it('should_build_kebab_case_type_uri_from_code', () => {
    expect(problemTypeFor(ErrorCode.CLIENT_NOT_FOUND)).toBe(
      'https://contracts.grupomariposa.dev/problems/client-not-found',
    );
  });

  it('should_build_all_required_members_without_errors_when_none_exist', () => {
    expect(buildProblemDetails(descriptor, context)).toEqual({
      type: 'https://contracts.grupomariposa.dev/problems/client-not-found',
      title: 'Client not found',
      status: 404,
      code: ErrorCode.CLIENT_NOT_FOUND,
      detail: 'Client CLI-1 does not exist',
      instance: '/clients/CLI-1',
      traceId: 'trace-1',
      timestamp: '2026-09-22T10:00:00.000Z',
    });
  });

  it('should_include_errors_when_descriptor_has_field_errors', () => {
    const errors = [{ field: 'clientId', message: 'invalid' }];

    expect(buildProblemDetails({ ...descriptor, errors }, context).errors).toEqual(errors);
  });
});
