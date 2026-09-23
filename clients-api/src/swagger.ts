import { INestApplication } from '@nestjs/common';
import { DocumentBuilder, OpenAPIObject, SwaggerModule } from '@nestjs/swagger';
import {
  BEARER_AUTH_DESCRIPTION,
  BEARER_AUTH_SCHEME,
  BEARER_FORMAT,
} from './shared/auth/bearer-auth.scheme';
import { OPENAPI_TYPES } from './shared/constants/openapi.constants';
import { ROUTES } from './shared/constants/routes.constants';

export const OPENAPI_VERSION = '3.1.0';
export const API_TITLE = 'Clients API';
export const API_VERSION = '1.0.0';
export const API_DESCRIPTION = 'Distributor master data. Owner: clients team.';

export function createOpenApiDocument(app: INestApplication): OpenAPIObject {
  const config = new DocumentBuilder()
    .setOpenAPIVersion(OPENAPI_VERSION)
    .setTitle(API_TITLE)
    .setVersion(API_VERSION)
    .setDescription(API_DESCRIPTION)
    .addBearerAuth(
      {
        type: OPENAPI_TYPES.HTTP_SECURITY,
        scheme: OPENAPI_TYPES.BEARER_SCHEME,
        bearerFormat: BEARER_FORMAT,
        description: BEARER_AUTH_DESCRIPTION,
      },
      BEARER_AUTH_SCHEME,
    )
    .build();
  return SwaggerModule.createDocument(app, config);
}

export function setupSwagger(app: INestApplication): void {
  SwaggerModule.setup(ROUTES.DOCS, app, () => createOpenApiDocument(app));
}
