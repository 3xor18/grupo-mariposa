package catalog

import (
	"context"
	"fmt"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	FieldProductID = "productId"
	FieldMarket    = "market"

	messageInvalidID      = "must match ^PRD-[A-Z0-9]{1,20}$"
	messageMarketRequired = "is required"
	messageInvalidMarket  = "must be one of MX, CO, PE"
	violationSeparator    = "; "
)

type Query struct {
	ProductID string
	Market    string
}

type Violation struct {
	Field   string
	Message string
}

type ValidationError struct {
	Violations []Violation
}

func (e *ValidationError) Error() string {
	parts := make([]string, 0, len(e.Violations))
	for _, v := range e.Violations {
		parts = append(parts, v.Field+" "+v.Message)
	}
	return "invalid query: " + strings.Join(parts, violationSeparator)
}

type Service struct {
	repository product.Repository
}

func NewService(repository product.Repository) *Service {
	return &Service{repository: repository}
}

func (s *Service) GetProduct(ctx context.Context, query Query) (product.Product, error) {
	id, market, err := validate(query)
	if err != nil {
		return product.Product{}, err
	}
	found, err := s.repository.FindByIDInMarket(ctx, id, market)
	if err != nil {
		return product.Product{}, fmt.Errorf("get product %s in %s: %w", id, market, err)
	}
	return found, nil
}

func validate(query Query) (product.ID, product.Market, error) {
	var violations []Violation
	id, err := product.ParseID(query.ProductID)
	if err != nil {
		violations = append(violations, Violation{Field: FieldProductID, Message: messageInvalidID})
	}
	market, err := product.ParseMarket(query.Market)
	if err != nil {
		violations = append(violations, marketViolation(query.Market))
	}
	if len(violations) > 0 {
		return "", "", &ValidationError{Violations: violations}
	}
	return id, market, nil
}

func marketViolation(raw string) Violation {
	if raw == "" {
		return Violation{Field: FieldMarket, Message: messageMarketRequired}
	}
	return Violation{Field: FieldMarket, Message: messageInvalidMarket}
}
