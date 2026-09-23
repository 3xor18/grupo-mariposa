package catalog

import (
	"context"
	"fmt"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	FieldProductID   = "productId"
	FieldMarket      = "market"
	FieldName        = "name"
	FieldStatus      = "status"
	FieldTaxCategory = "taxCategory"
	FieldBody        = "body"

	messageInvalidID      = "must match ^PRD-[A-Z0-9]{1,20}$"
	messageRequired       = "is required"
	messageMarketOneOf    = "must be one of "
	messageInvalidStatus  = "must be one of ACTIVE, DISCONTINUED"
	messageInvalidTax     = "must be one of STANDARD, REDUCED, EXEMPT"
	messageInvalidName    = "must have between 1 and 120 characters"
	messageEmptyPatch     = "must contain at least one of name, status, taxCategory"
	violationSeparator    = "; "
	marketCodesSeparator  = ", "
	violationFieldMessage = "%s %s"
)

type MarketCatalog interface {
	Contains(code product.Market) bool
	Codes() []product.Market
}

type EventFactory interface {
	Build(p product.Product) (product.ChangeEvent, error)
}

type Query struct {
	ProductID string
	Market    string
}

type UpdateCommand struct {
	ProductID    string
	Market       string
	Name         *string
	Status       *string
	TaxCategory  *string
	Precondition *product.Precondition
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
		parts = append(parts, fmt.Sprintf(violationFieldMessage, v.Field, v.Message))
	}
	return "invalid request: " + strings.Join(parts, violationSeparator)
}

type Service struct {
	repository product.Repository
	writer     product.Writer
	markets    MarketCatalog
	events     EventFactory
}

func NewService(repository product.Repository, writer product.Writer, markets MarketCatalog,
	events EventFactory,
) *Service {
	return &Service{repository: repository, writer: writer, markets: markets, events: events}
}

func (s *Service) GetProduct(ctx context.Context, query Query) (product.Product, error) {
	v := &validator{markets: s.markets}
	id, market := v.id(query.ProductID), v.market(query.Market)
	if err := v.err(); err != nil {
		return product.Product{}, err
	}
	found, err := s.repository.FindByIDInMarket(ctx, id, market)
	if err != nil {
		return product.Product{}, fmt.Errorf("get product %s in %s: %w", id, market, err)
	}
	return found, nil
}

func (s *Service) UpdateProduct(ctx context.Context, cmd UpdateCommand) (product.Product, error) {
	v := &validator{markets: s.markets}
	request := product.UpdateRequest{
		ID:           v.id(cmd.ProductID),
		Market:       v.market(cmd.Market),
		Patch:        v.patch(cmd),
		Precondition: cmd.Precondition,
		NewEvent:     s.events.Build,
	}
	if err := v.err(); err != nil {
		return product.Product{}, err
	}
	updated, err := s.writer.Update(ctx, request)
	if err != nil {
		return product.Product{}, fmt.Errorf("update product %s in %s: %w", request.ID,
			request.Market, err)
	}
	return updated, nil
}

type validator struct {
	markets    MarketCatalog
	violations []Violation
}

func (v *validator) add(field, message string) {
	v.violations = append(v.violations, Violation{Field: field, Message: message})
}

func (v *validator) err() error {
	if len(v.violations) == 0 {
		return nil
	}
	return &ValidationError{Violations: v.violations}
}

func (v *validator) id(raw string) product.ID {
	id, err := product.ParseID(raw)
	if err != nil {
		v.add(FieldProductID, messageInvalidID)
	}
	return id
}

func (v *validator) market(raw string) product.Market {
	if raw == "" {
		v.add(FieldMarket, messageRequired)
		return ""
	}
	market, err := product.ParseMarket(raw)
	if err != nil || !v.markets.Contains(market) {
		v.add(FieldMarket, messageMarketOneOf+v.marketCodes())
	}
	return market
}

func (v *validator) marketCodes() string {
	codes := v.markets.Codes()
	names := make([]string, 0, len(codes))
	for _, code := range codes {
		names = append(names, string(code))
	}
	return strings.Join(names, marketCodesSeparator)
}

func (v *validator) patch(cmd UpdateCommand) product.Patch {
	if cmd.Name == nil && cmd.Status == nil && cmd.TaxCategory == nil {
		v.add(FieldBody, messageEmptyPatch)
		return product.Patch{}
	}
	return product.Patch{
		Name: parseOptional(v, cmd.Name, FieldName, messageInvalidName, product.ParseName),
		Status: parseOptional(v, cmd.Status, FieldStatus, messageInvalidStatus,
			product.ParseStatus),
		TaxCategory: parseOptional(v, cmd.TaxCategory, FieldTaxCategory, messageInvalidTax,
			product.ParseTaxCategory),
	}
}

func parseOptional[T any](v *validator, raw *string, field, message string,
	parse func(string) (T, error),
) *T {
	if raw == nil {
		return nil
	}
	parsed, err := parse(*raw)
	if err != nil {
		v.add(field, message)
		return nil
	}
	return &parsed
}
