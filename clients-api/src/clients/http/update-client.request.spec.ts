import { plainToInstance } from 'class-transformer';
import { validate } from 'class-validator';
import { ErrorCode } from '../../shared/errors/error-code.enum';
import { ProblemException } from '../../shared/errors/problem.exception';
import { ClientStatus } from '../domain/client-status.enum';
import { Segment } from '../domain/segment.enum';
import { TaxRegime } from '../domain/tax-regime.enum';
import {
  EMPTY_CHANGES_MESSAGE,
  RequireChangesPipe,
  toClientChanges,
  UpdateClientRequest,
} from './update-client.request';

const request = (body: object): UpdateClientRequest => plainToInstance(UpdateClientRequest, body);

describe('UpdateClientRequest', () => {
  it('should_accept_any_subset_of_the_editable_fields', async () => {
    await expect(validate(request({ status: 'BLOCKED' }))).resolves.toEqual([]);
    await expect(
      validate(request({ status: 'ACTIVE', segment: 'RETAIL', taxRegime: 'GENERAL' })),
    ).resolves.toEqual([]);
  });

  it.each([{ status: 'DELETED' }, { segment: 1 }, { taxRegime: 'NONE' }])(
    'should_reject_unknown_values_%j',
    async (body) => {
      await expect(validate(request(body))).resolves.toHaveLength(1);
    },
  );

  it('should_map_only_the_provided_fields_to_changes', () => {
    expect(toClientChanges(request({ status: ClientStatus.BLOCKED }))).toEqual({
      status: ClientStatus.BLOCKED,
    });
    expect(
      toClientChanges(request({ segment: Segment.WHOLESALE, taxRegime: TaxRegime.SIMPLIFIED })),
    ).toEqual({ segment: Segment.WHOLESALE, taxRegime: TaxRegime.SIMPLIFIED });
  });
});

describe('RequireChangesPipe', () => {
  it('should_pass_requests_with_at_least_one_change', () => {
    const body = request({ taxRegime: TaxRegime.GENERAL });

    expect(new RequireChangesPipe().transform(body)).toBe(body);
  });

  it('should_reject_empty_changes_with_a_field_error', () => {
    const attempt = (): unknown => new RequireChangesPipe().transform(request({}));

    expect(attempt).toThrow(
      new ProblemException(ErrorCode.VALIDATION_ERROR, EMPTY_CHANGES_MESSAGE),
    );
    try {
      attempt();
    } catch (error: unknown) {
      expect(error).toMatchObject({
        options: { errors: [{ field: 'body', message: EMPTY_CHANGES_MESSAGE }] },
      });
    }
  });
});
