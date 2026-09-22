import { BeforeApplicationShutdown, Injectable, OnApplicationBootstrap } from '@nestjs/common';

@Injectable()
export class ReadinessState implements OnApplicationBootstrap, BeforeApplicationShutdown {
  private ready = false;

  isReady(): boolean {
    return this.ready;
  }

  onApplicationBootstrap(): void {
    this.ready = true;
  }

  beforeApplicationShutdown(): void {
    this.ready = false;
  }
}
