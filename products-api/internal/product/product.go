package product

import (
	"context"
	"errors"
	"fmt"
	"regexp"
	"strings"
	"time"
	"unicode/utf8"
)

type (
	ID          string
	Market      string
	Status      string
	TaxCategory string
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

const (
	InitialVersion  int64 = 1
	MaxNameLength         = 120
	eventKeyPattern       = "%s:%s"
)

var (
	ErrNotFound           = errors.New("product not found")
	ErrVersionConflict    = errors.New("product version conflict")
	ErrInvalidID          = errors.New("invalid product id")
	ErrInvalidMarket      = errors.New("invalid market")
	ErrInvalidStatus      = errors.New("invalid status")
	ErrInvalidTaxCategory = errors.New("invalid tax category")
	ErrInvalidName        = errors.New("invalid name")
)

var (
	idPattern     = regexp.MustCompile(`^PRD-[A-Z0-9]{1,20}$`)
	marketPattern = regexp.MustCompile(`^[A-Z]{2}$`)
)

type Product struct {
	ID          ID
	Market      Market
	Name        string
	SKU         string
	Status      Status
	TaxCategory TaxCategory
	Version     int64
}

type Patch struct {
	Name        *string
	Status      *Status
	TaxCategory *TaxCategory
}

type ChangeEvent struct {
	ID         string
	Key        string
	OccurredAt time.Time
	Payload    []byte
}

type UpdateRequest struct {
	ID              ID
	Market          Market
	Patch           Patch
	ExpectedVersion *int64
	NewEvent        func(Product) (ChangeEvent, error)
}

type Repository interface {
	FindByIDInMarket(ctx context.Context, id ID, market Market) (Product, error)
}

type Writer interface {
	Update(ctx context.Context, request UpdateRequest) (Product, error)
}

func (p Product) Apply(patch Patch) Product {
	updated := p
	if patch.Name != nil {
		updated.Name = *patch.Name
	}
	if patch.Status != nil {
		updated.Status = *patch.Status
	}
	if patch.TaxCategory != nil {
		updated.TaxCategory = *patch.TaxCategory
	}
	updated.Version++
	return updated
}

func (p Product) EventKey() string {
	return fmt.Sprintf(eventKeyPattern, p.Market, p.ID)
}

func (r UpdateRequest) Matches(current Product) bool {
	return r.ExpectedVersion == nil || *r.ExpectedVersion == current.Version
}

func ParseID(raw string) (ID, error) {
	if !idPattern.MatchString(raw) {
		return "", fmt.Errorf("%w: %q", ErrInvalidID, raw)
	}
	return ID(raw), nil
}

func ParseMarket(raw string) (Market, error) {
	if !marketPattern.MatchString(raw) {
		return "", fmt.Errorf("%w: %q", ErrInvalidMarket, raw)
	}
	return Market(raw), nil
}

func ParseStatus(raw string) (Status, error) {
	switch status := Status(raw); status {
	case StatusActive, StatusDiscontinued:
		return status, nil
	default:
		return "", fmt.Errorf("%w: %q", ErrInvalidStatus, raw)
	}
}

func ParseTaxCategory(raw string) (TaxCategory, error) {
	switch category := TaxCategory(raw); category {
	case TaxStandard, TaxReduced, TaxExempt:
		return category, nil
	default:
		return "", fmt.Errorf("%w: %q", ErrInvalidTaxCategory, raw)
	}
}

func ParseName(raw string) (string, error) {
	if strings.TrimSpace(raw) == "" || utf8.RuneCountInString(raw) > MaxNameLength {
		return "", fmt.Errorf("%w: length must be 1 to %d characters", ErrInvalidName,
			MaxNameLength)
	}
	return raw, nil
}
