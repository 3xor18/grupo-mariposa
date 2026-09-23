import { CanActivate, ExecutionContext, Inject, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { ACCESS_TOKEN_VERIFIER, AccessTokenVerifier } from './access-token-verifier';
import { AuthenticatedRequest } from './principal';
import { AccessRole, REQUIRED_ACCESS_ROLE_KEY } from './access-role';
import { IS_PUBLIC_KEY } from './public.decorator';

@Injectable()
export class JwtAuthGuard implements CanActivate {
  constructor(
    private readonly reflector: Reflector,
    @Inject(ACCESS_TOKEN_VERIFIER) private readonly verifier: AccessTokenVerifier,
  ) {}

  async canActivate(context: ExecutionContext): Promise<boolean> {
    if (!this.isPublic(context)) {
      const request = context.switchToHttp().getRequest<AuthenticatedRequest>();
      const principal = await this.verifier.authenticate(
        request.headers.authorization,
        this.requiredRole(context),
      );
      if (principal !== undefined) {
        request.principal = principal;
      }
    }
    return true;
  }

  private requiredRole(context: ExecutionContext): AccessRole {
    return (
      this.reflector.getAllAndOverride<AccessRole | undefined>(REQUIRED_ACCESS_ROLE_KEY, [
        context.getHandler(),
        context.getClass(),
      ]) ?? AccessRole.READER
    );
  }

  private isPublic(context: ExecutionContext): boolean {
    return (
      this.reflector.getAllAndOverride<boolean | undefined>(IS_PUBLIC_KEY, [
        context.getHandler(),
        context.getClass(),
      ]) === true
    );
  }
}
