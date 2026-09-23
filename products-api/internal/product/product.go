package product

import (
	"context"
	"errors"
	"fmt"
	"regexp"
)

type ID string

type Market string

type Status string

type TaxCategory string

const (
	MarketMX Market = "MX"
	MarketCO Market = "CO"
	MarketPE Market = "PE"
)

const (
	StatusActive       Status = "ACTIVE"
	StatusDiscontinued Status = "DISCONTINUED"
)

const (
	TaxStandard TaxCategory = "STANDARD"
	TaxReduced  TaxCategory = "REDUCED"
	TaxExempt   TaxCategory = "EXEMPT"
)

var (
	ErrNotFound      = errors.New("product not found")
	ErrInvalidID     = errors.New("invalid product id")
	ErrInvalidMarket = errors.New("invalid market")
)

var idPattern = regexp.MustCompile(`^PRD-[A-Z0-9]{1,20}$`)

type Product struct {
	ID          ID
	Name        string
	SKU         string
	Status      Status
	TaxCategory TaxCategory
}

type Repository interface {
	FindByIDInMarket(ctx context.Context, id ID, market Market) (Product, error)
}

func ParseID(raw string) (ID, error) {
	if !idPattern.MatchString(raw) {
		return "", fmt.Errorf("%w: %q", ErrInvalidID, raw)
	}
	return ID(raw), nil
}

func ParseMarket(raw string) (Market, error) {
	market := Market(raw)
	switch market {
	case MarketMX, MarketCO, MarketPE:
		return market, nil
	default:
		return "", fmt.Errorf("%w: %q", ErrInvalidMarket, raw)
	}
}

func Markets() []Market {
	return []Market{MarketMX, MarketCO, MarketPE}
}
