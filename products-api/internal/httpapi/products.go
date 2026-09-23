package httpapi

import (
	"context"
	"errors"
	"fmt"
	"net/http"

	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	pathProductID      = "productId"
	queryMarket        = "market"
	detailValidation   = "The request has invalid parameters."
	detailNotFound     = "Product %s is not available in market %s."
	detailTimeout      = "The request could not be completed in time."
	detailClientClosed = "The client closed the request before it completed."
	detailInternal     = "An unexpected error occurred."
	logLookupFailed    = "product lookup failed"
	logLookupCancelled = "product lookup cancelled"
)

type ProductFinder interface {
	GetProduct(ctx context.Context, query catalog.Query) (product.Product, error)
}

type productResponse struct {
	ProductID   string `json:"productId"`
	Name        string `json:"name"`
	SKU         string `json:"sku"`
	Status      string `json:"status"`
	TaxCategory string `json:"taxCategory"`
}

type productHandler struct {
	finder ProductFinder
	responder
}

func (h productHandler) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	query := catalog.Query{
		ProductID: r.PathValue(pathProductID),
		Market:    r.URL.Query().Get(queryMarket),
	}
	found, err := h.finder.GetProduct(r.Context(), query)
	if err != nil {
		h.fail(w, r, query, err)
		return
	}
	h.json(w, r, http.StatusOK, toResponse(found))
}

func (h productHandler) fail(w http.ResponseWriter, r *http.Request, q catalog.Query, err error) {
	var invalid *catalog.ValidationError
	switch {
	case errors.As(err, &invalid):
		h.problem(w, r, kindValidation, detailValidation, toFieldErrors(invalid)...)
	case errors.Is(err, product.ErrNotFound):
		h.problem(w, r, kindNotFound, fmt.Sprintf(detailNotFound, q.ProductID, q.Market))
	case errors.Is(err, context.DeadlineExceeded):
		h.logger.WarnContext(r.Context(), logLookupCancelled, logKeyError, err.Error())
		h.problem(w, r, kindUnavailable, detailTimeout)
	case errors.Is(err, context.Canceled):
		h.logger.InfoContext(r.Context(), logLookupCancelled, logKeyError, err.Error())
		h.problem(w, r, kindClientClosed, detailClientClosed)
	default:
		h.logger.ErrorContext(r.Context(), logLookupFailed, logKeyError, err.Error())
		h.problem(w, r, kindInternal, detailInternal)
	}
}

func toResponse(p product.Product) productResponse {
	return productResponse{
		ProductID:   string(p.ID),
		Name:        p.Name,
		SKU:         p.SKU,
		Status:      string(p.Status),
		TaxCategory: string(p.TaxCategory),
	}
}

func toFieldErrors(invalid *catalog.ValidationError) []fieldError {
	fields := make([]fieldError, 0, len(invalid.Violations))
	for _, v := range invalid.Violations {
		fields = append(fields, fieldError{Field: v.Field, Message: v.Message})
	}
	return fields
}
