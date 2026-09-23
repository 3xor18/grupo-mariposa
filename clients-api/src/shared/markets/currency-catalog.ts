import { MarketCatalogError, MarketDefinition } from './market-catalog';

export const DEFAULT_PLATFORM_CURRENCIES = 'MXN:2,COP:2,PEN:2,CLP:0,USD:2';

const CURRENCY_CODE_PATTERN = /^[A-Z]{3}$/;
const DIGITS_PATTERN = /^[0-4]$/;
const ENTRY_SEPARATOR = ',';
const FIELD_SEPARATOR = ':';
const FIELDS_PER_CURRENCY = 2;

export interface CurrencyDefinition {
  readonly code: string;
  readonly minorUnits: number;
}

type CurrencyFields = [code: string, digits: string];

function isCurrencyFields(fields: string[]): fields is CurrencyFields {
  return fields.length === FIELDS_PER_CURRENCY;
}

function parseCurrency(entry: string): CurrencyDefinition {
  const fields = entry.split(FIELD_SEPARATOR).map((field) => field.trim());
  if (
    !isCurrencyFields(fields) ||
    !CURRENCY_CODE_PATTERN.test(fields[0]) ||
    !DIGITS_PATTERN.test(fields[1])
  ) {
    throw new MarketCatalogError(`"${entry}" is not CODE:DIGITS with 0 to 4 digits`);
  }
  const [code, digits] = fields;
  return Object.freeze({ code, minorUnits: Number(digits) });
}

export function parseCurrencies(raw: string): readonly CurrencyDefinition[] {
  const currencies = raw
    .split(ENTRY_SEPARATOR)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
    .map(parseCurrency);
  if (currencies.length === 0) {
    throw new MarketCatalogError('at least one currency is required');
  }
  const codes = new Set<string>();
  for (const currency of currencies) {
    if (codes.has(currency.code)) {
      throw new MarketCatalogError(`currency ${currency.code} is declared twice`);
    }
    codes.add(currency.code);
  }
  return Object.freeze(currencies);
}

export function undeclaredCurrencies(
  markets: readonly MarketDefinition[],
  currencies: readonly CurrencyDefinition[],
): string[] {
  const declared = new Set(currencies.map((currency) => currency.code));
  return markets
    .filter((market) => !declared.has(market.currency))
    .map((market) => `market ${market.code} uses undeclared currency ${market.currency}`);
}
