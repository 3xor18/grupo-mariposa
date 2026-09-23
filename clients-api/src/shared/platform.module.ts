import { Global, Module } from '@nestjs/common';
import { APP_CONFIG, AppConfig } from '../config/app-config';
import { ID_GENERATOR, uuidV7Generator } from './ids/uuid-v7';
import { MARKET_CATALOG, MarketCatalog } from './markets/market-catalog';
import { CLOCK, systemClock } from './time/clock';

export function marketCatalogOf(config: AppConfig): MarketCatalog {
  return new MarketCatalog(config.markets);
}

@Global()
@Module({
  providers: [
    { provide: MARKET_CATALOG, useFactory: marketCatalogOf, inject: [APP_CONFIG] },
    { provide: CLOCK, useValue: systemClock },
    { provide: ID_GENERATOR, useValue: uuidV7Generator },
  ],
  exports: [MARKET_CATALOG, CLOCK, ID_GENERATOR],
})
export class PlatformModule {}
