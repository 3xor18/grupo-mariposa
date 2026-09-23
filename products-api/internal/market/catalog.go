package market

import (
	"errors"
	"fmt"
	"regexp"
	"slices"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	DefaultMarkets = "MX:MXN:es-MX,CO:COP:es-CO,PE:PEN:es-PE,CL:CLP:es-CL,EC:USD:es-EC"
	entrySeparator = ","
	partSeparator  = ":"
	partsPerEntry  = 3
	currencyPart   = 1
	localePart     = 2
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

type Catalog struct {
	markets []Market
}

func Parse(raw string) (Catalog, error) {
	var markets []Market
	for _, entry := range strings.Split(raw, entrySeparator) {
		if strings.TrimSpace(entry) == "" {
			continue
		}
		parsed, err := parseEntry(strings.TrimSpace(entry))
		if err != nil {
			return Catalog{}, err
		}
		if slices.ContainsFunc(markets, func(m Market) bool { return m.Code == parsed.Code }) {
			return Catalog{}, fmt.Errorf("%w: duplicated market %s", ErrInvalidCatalog, parsed.Code)
		}
		markets = append(markets, parsed)
	}
	if len(markets) == 0 {
		return Catalog{}, fmt.Errorf("%w: no markets defined", ErrInvalidCatalog)
	}
	return Catalog{markets: markets}, nil
}

func parseEntry(entry string) (Market, error) {
	parts := strings.Split(entry, partSeparator)
	if len(parts) != partsPerEntry {
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
