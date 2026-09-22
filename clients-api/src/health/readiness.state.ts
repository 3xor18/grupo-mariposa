import { Injectable, OnApplicationBootstrap } from '@nestjs/common';

@Injectable()
export class ReadinessState implements OnApplicationBootstrap {
  private ready = false;

  isReady(): boolean {
    return this.ready;
  }

  onApplicationBootstrap(): void {
    this.ready = true;
  }

  markDraining(): void {
    this.ready = false;
  }
}
