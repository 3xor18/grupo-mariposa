export const MARKET_CATALOG = Symbol('MARKET_CATALOG');

export const DEFAULT_PLATFORM_MARKETS =
  'MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC';

export const MARKET_CODE_PATTERN = /^[A-Z]{2}$/;
const CURRENCY_CODE_PATTERN = /^[A-Z]{3}$/;
const LOCALE_PATTERN = /^[a-z]{2}-[A-Z]{2}$/;
const ENTRY_SEPARATOR = ',';
const FIELD_SEPARATOR = ':';
const FIELDS_PER_MARKET = 3;

export interface MarketDefinition {
  readonly code: string;
  readonly currency: string;
  readonly locale: string;
}

export class MarketCatalogError extends Error {
  constructor(reason: string) {
    super(`Invalid market catalog: ${reason}`);
    this.name = MarketCatalogError.name;
  }
}

type MarketFields = [code: string, currency: string, locale: string];

function isMarketFields(fields: string[]): fields is MarketFields {
  return fields.length === FIELDS_PER_MARKET;
}

function isValidMarket([code, currency, locale]: MarketFields): boolean {
  return (
    MARKET_CODE_PATTERN.test(code) &&
    CURRENCY_CODE_PATTERN.test(currency) &&
    LOCALE_PATTERN.test(locale)
  );
}

function parseMarket(entry: string): MarketDefinition {
  const fields = entry.split(FIELD_SEPARATOR).map((field) => field.trim());
  if (!isMarketFields(fields) || !isValidMarket(fields)) {
    throw new MarketCatalogError(`"${entry}" is not CODE:CURRENCY:locale`);
  }
  const [code, currency, locale] = fields;
  return Object.freeze({ code, currency, locale });
}

function rejectDuplicates(markets: readonly MarketDefinition[]): void {
  const codes = new Set<string>();
  for (const market of markets) {
    if (codes.has(market.code)) {
      throw new MarketCatalogError(`market ${market.code} is declared twice`);
    }
    codes.add(market.code);
  }
}

export function parseMarkets(raw: string): readonly MarketDefinition[] {
  const markets = raw
    .split(ENTRY_SEPARATOR)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0)
    .map(parseMarket);
  if (markets.length === 0) {
    throw new MarketCatalogError('at least one market is required');
  }
  rejectDuplicates(markets);
  return Object.freeze(markets);
}

export class MarketCatalog {
  private readonly markets: ReadonlyMap<string, MarketDefinition>;

  constructor(definitions: readonly MarketDefinition[]) {
    this.markets = new Map(definitions.map((market) => [market.code, market]));
  }

  get codes(): readonly string[] {
    return [...this.markets.keys()];
  }

  includes(code: string): boolean {
    return this.markets.has(code);
  }
}
