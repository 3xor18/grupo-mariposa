import 'reflect-metadata';
import { NestFactory } from '@nestjs/core';
import { NestExpressApplication } from '@nestjs/platform-express';
import { AppModule } from './app.module';
import { configureApp } from './app.setup';
import { exitFatally, registerFatalHandlers } from './bootstrap/fatal-handler';
import { loadConfig } from './config/load-config';
import { loadRemoteConfig } from './config/remote/load-remote-config';

async function bootstrap(): Promise<void> {
  const config = loadConfig(await loadRemoteConfig(process.env));
  const app = await NestFactory.create<NestExpressApplication>(AppModule.forRoot(config), {
    bufferLogs: true,
    forceCloseConnections: true,
    abortOnError: false,
  });
  configureApp(app);
  app.enableShutdownHooks();
  await app.listen(config.port);
}

registerFatalHandlers(process, (error: unknown) => {
  exitFatally(error);
});
bootstrap().catch((error: unknown) => {
  exitFatally(error);
});
