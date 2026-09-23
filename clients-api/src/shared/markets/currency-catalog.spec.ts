import {
  DEFAULT_PLATFORM_CURRENCIES,
  parseCurrencies,
  undeclaredCurrencies,
} from './currency-catalog';
import { DEFAULT_PLATFORM_MARKETS, MarketCatalogError, parseMarkets } from './market-catalog';

describe('parseCurrencies', () => {
  it('should_parse_the_default_currencies_with_minor_units', () => {
    expect(parseCurrencies(DEFAULT_PLATFORM_CURRENCIES)).toEqual([
      { code: 'MXN', minorUnits: 2 },
      { code: 'COP', minorUnits: 2 },
      { code: 'PEN', minorUnits: 2 },
      { code: 'CLP', minorUnits: 0 },
      { code: 'USD', minorUnits: 2 },
    ]);
  });

  it('should_trim_entries_and_accept_zero_to_four_digits', () => {
    expect(parseCurrencies(' KWD : 3 , BHD:4,')).toEqual([
      { code: 'KWD', minorUnits: 3 },
      { code: 'BHD', minorUnits: 4 },
    ]);
  });

  it.each([
    ['', 'at least one currency is required'],
    ['MXN', '"MXN" is not CODE:DIGITS with 0 to 4 digits'],
    ['MXN:5', '"MXN:5" is not CODE:DIGITS with 0 to 4 digits'],
    ['MX:2', '"MX:2" is not CODE:DIGITS with 0 to 4 digits'],
    ['MXN:two', '"MXN:two" is not CODE:DIGITS with 0 to 4 digits'],
    ['MXN:2:x', '"MXN:2:x" is not CODE:DIGITS with 0 to 4 digits'],
    ['MXN:2,MXN:0', 'currency MXN is declared twice'],
  ])('should_reject_invalid_currencies_%p', (raw, reason) => {
    expect(() => parseCurrencies(raw)).toThrow(new MarketCatalogError(reason));
  });
});

describe('undeclaredCurrencies', () => {
  it('should_accept_catalogs_whose_market_currencies_are_declared', () => {
    expect(
      undeclaredCurrencies(
        parseMarkets(DEFAULT_PLATFORM_MARKETS),
        parseCurrencies(DEFAULT_PLATFORM_CURRENCIES),
      ),
    ).toEqual([]);
  });

  it('should_list_every_market_using_an_undeclared_currency', () => {
    expect(
      undeclaredCurrencies(parseMarkets('MX:MXN:es-MX,AR:ARS:es-AR'), parseCurrencies('USD:2')),
    ).toEqual(['market MX uses undeclared currency MXN', 'market AR uses undeclared currency ARS']);
  });
});
