package market

import (
	"errors"
	"fmt"
	"regexp"
	"slices"
	"strconv"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	DefaultMarkets     = "MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC"
	DefaultCurrencies  = "MXN:2,COP:2,PEN:2,CLP:0,USD:2"
	entrySeparator     = ","
	partSeparator      = ":"
	marketParts        = 3
	currencyParts      = 2
	currencyPart       = 1
	localePart         = 2
	digitsPart         = 1
	maxDigits          = 4
	digitsNumberFormat = 10
	digitsBitSize      = 8
)

var (
	ErrInvalidCatalog = errors.New("invalid market catalog")
	currencyPattern   = regexp.MustCompile(`^[A-Z]{3}$`)
	localePattern     = regexp.MustCompile(`^[a-z]{2}-[A-Z]{2}$`)
)

type Market struct {
	Code     product.Market
	Currency string
	Locale   string
}

type Currency struct {
	Code   string
	Digits int
}

type Catalog struct {
	markets    []Market
	currencies []Currency
}

func Parse(markets, currencies string) (Catalog, error) {
	declared, err := parseList(currencies, parseCurrency, func(c Currency) string { return c.Code })
	if err != nil {
		return Catalog{}, err
	}
	parsed, err := parseList(markets, parseMarket,
		func(m Market) string { return string(m.Code) })
	if err != nil {
		return Catalog{}, err
	}
	for _, m := range parsed {
		if !slices.ContainsFunc(declared, func(c Currency) bool { return c.Code == m.Currency }) {
			return Catalog{}, fmt.Errorf("%w: market %s uses undeclared currency %s",
				ErrInvalidCatalog, m.Code, m.Currency)
		}
	}
	return Catalog{markets: parsed, currencies: declared}, nil
}

func parseList[T any](raw string, parse func(string) (T, error), key func(T) string,
) ([]T, error) {
	var items []T
	seen := map[string]bool{}
	for _, entry := range strings.Split(raw, entrySeparator) {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		item, err := parse(entry)
		if err != nil {
			return nil, err
		}
		if seen[key(item)] {
			return nil, fmt.Errorf("%w: duplicated %s", ErrInvalidCatalog, key(item))
		}
		seen[key(item)] = true
		items = append(items, item)
	}
	if len(items) == 0 {
		return nil, fmt.Errorf("%w: empty list", ErrInvalidCatalog)
	}
	return items, nil
}

func parseMarket(entry string) (Market, error) {
	parts := strings.Split(entry, partSeparator)
	if len(parts) != marketParts {
		return Market{}, fmt.Errorf("%w: %q must be CODE:CURRENCY:LOCALE", ErrInvalidCatalog, entry)
	}
	code, err := product.ParseMarket(parts[0])
	if err != nil {
		return Market{}, fmt.Errorf("%w: %w", ErrInvalidCatalog, err)
	}
	currency, locale := parts[currencyPart], parts[localePart]
	if !currencyPattern.MatchString(currency) || !localePattern.MatchString(locale) {
		return Market{}, fmt.Errorf("%w: %q has an invalid currency or locale", ErrInvalidCatalog,
			entry)
	}
	return Market{Code: code, Currency: currency, Locale: locale}, nil
}

func parseCurrency(entry string) (Currency, error) {
	parts := strings.Split(entry, partSeparator)
	if len(parts) != currencyParts || !currencyPattern.MatchString(parts[0]) {
		return Currency{}, fmt.Errorf("%w: %q must be CODE:DIGITS", ErrInvalidCatalog, entry)
	}
	digits, err := strconv.ParseUint(parts[digitsPart], digitsNumberFormat, digitsBitSize)
	if err != nil || digits > maxDigits {
		return Currency{}, fmt.Errorf("%w: %q digits must be 0 to %d", ErrInvalidCatalog, entry,
			maxDigits)
	}
	return Currency{Code: parts[0], Digits: int(digits)}, nil
}

func (c Catalog) Contains(code product.Market) bool {
	return slices.ContainsFunc(c.markets, func(m Market) bool { return m.Code == code })
}

func (c Catalog) Codes() []product.Market {
	codes := make([]product.Market, 0, len(c.markets))
	for _, m := range c.markets {
		codes = append(codes, m.Code)
	}
	return codes
}

func (c Catalog) Markets() []Market {
	return slices.Clone(c.markets)
}

func (c Catalog) Currencies() []Currency {
	return slices.Clone(c.currencies)
}
