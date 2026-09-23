import { CustomDecorator, SetMetadata } from '@nestjs/common';

export enum AccessRole {
  READER = 'READER',
  ADMIN = 'ADMIN',
}

export const REQUIRED_ACCESS_ROLE_KEY = 'auth:requiredAccessRole';

export const RequireRole = (role: AccessRole): CustomDecorator =>
  SetMetadata(REQUIRED_ACCESS_ROLE_KEY, role);
