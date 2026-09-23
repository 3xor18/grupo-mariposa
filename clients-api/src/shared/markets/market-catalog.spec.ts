import { testConfig } from '../../../test/support/test-app';
import { marketCatalogOf } from '../platform.module';
import {
  DEFAULT_PLATFORM_MARKETS,
  MarketCatalog,
  MarketCatalogError,
  parseMarkets,
} from './market-catalog';

describe('parseMarkets', () => {
  it('should_parse_the_default_five_market_catalog', () => {
    expect(parseMarkets(DEFAULT_PLATFORM_MARKETS)).toEqual([
      { code: 'MX', currency: 'MXN', locale: 'es-MX' },
      { code: 'CO', currency: 'COP', locale: 'es-CO' },
      { code: 'PE', currency: 'PEN', locale: 'es-PE' },
      { code: 'CL', currency: 'CLP', locale: 'es-CL' },
      { code: 'EC', currency: 'USD', locale: 'es-EC' },
    ]);
  });

  it('should_trim_entries_and_allow_shared_currencies', () => {
    expect(parseMarkets(' EC:USD:es-EC , PA : USD : es-PA ,')).toEqual([
      { code: 'EC', currency: 'USD', locale: 'es-EC' },
      { code: 'PA', currency: 'USD', locale: 'es-PA' },
    ]);
  });

  it.each([
    ['', 'at least one market is required'],
    [' , ', 'at least one market is required'],
    ['MX:MXN', '"MX:MXN" is not CODE:CURRENCY:locale'],
    ['MEX:MXN:es-MX', '"MEX:MXN:es-MX" is not CODE:CURRENCY:locale'],
    ['MX:MX:es-MX', '"MX:MX:es-MX" is not CODE:CURRENCY:locale'],
    ['MX:MXN:esMX', '"MX:MXN:esMX" is not CODE:CURRENCY:locale'],
    ['MX:MXN:es-MX:x', '"MX:MXN:es-MX:x" is not CODE:CURRENCY:locale'],
    ['MX:MXN:es-MX,MX:USD:es-MX', 'market MX is declared twice'],
  ])('should_reject_invalid_catalog_%p', (raw, reason) => {
    expect(() => parseMarkets(raw)).toThrow(new MarketCatalogError(reason));
  });
});

describe('MarketCatalog', () => {
  it('should_answer_membership_and_list_codes', () => {
    const catalog = new MarketCatalog(parseMarkets('MX:MXN:es-MX,CL:CLP:es-CL'));

    expect(catalog.codes).toEqual(['MX', 'CL']);
    expect(catalog.includes('CL')).toBe(true);
    expect(catalog.includes('PE')).toBe(false);
  });

  it('should_be_built_from_configuration', () => {
    expect(marketCatalogOf(testConfig('http://unused')).codes).toEqual([
      'MX',
      'CO',
      'PE',
      'CL',
      'EC',
    ]);
  });
});
