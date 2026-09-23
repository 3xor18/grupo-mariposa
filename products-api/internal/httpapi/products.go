package httpapi

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	"strconv"

	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	pathProductID      = "productId"
	queryMarket        = "market"
	detailValidation   = "The request has invalid parameters."
	detailNotFound     = "Product %s is not available in market %s."
	detailConflict     = "The product changed since it was read; reload it and retry."
	detailTimeout      = "The request could not be completed in time."
	detailClientClosed = "The client closed the request before it completed."
	detailInternal     = "An unexpected error occurred."
	logLookupFailed    = "product request failed"
	logLookupCancelled = "product request cancelled"
	etagQuote          = `"`
	versionBase        = 10
)

type ProductService interface {
	GetProduct(ctx context.Context, query catalog.Query) (product.Product, error)
	UpdateProduct(ctx context.Context, cmd catalog.UpdateCommand) (product.Product, error)
}

type productResponse struct {
	ProductID   string `json:"productId"`
	Name        string `json:"name"`
	SKU         string `json:"sku"`
	Status      string `json:"status"`
	TaxCategory string `json:"taxCategory"`
	Version     int64  `json:"version"`
}

type productHandler struct {
	service ProductService
	responder
}

func (h productHandler) get(w http.ResponseWriter, r *http.Request) {
	query := catalog.Query{
		ProductID: r.PathValue(pathProductID),
		Market:    r.URL.Query().Get(queryMarket),
	}
	found, err := h.service.GetProduct(r.Context(), query)
	if err != nil {
		h.fail(w, r, query.ProductID, query.Market, err)
		return
	}
	h.writeProduct(w, r, found)
}

func (h productHandler) patch(w http.ResponseWriter, r *http.Request) {
	cmd, invalid := decodeUpdate(w, r)
	if len(invalid) > 0 {
		h.problem(w, r, kindValidation, detailValidation, invalid...)
		return
	}
	updated, err := h.service.UpdateProduct(r.Context(), cmd)
	if err != nil {
		h.fail(w, r, cmd.ProductID, cmd.Market, err)
		return
	}
	h.writeProduct(w, r, updated)
}

func (h productHandler) writeProduct(w http.ResponseWriter, r *http.Request, p product.Product) {
	w.Header().Set(headerETag, etag(p.Version))
	h.json(w, r, http.StatusOK, toResponse(p))
}

func (h productHandler) fail(w http.ResponseWriter, r *http.Request, id, market string,
	err error,
) {
	var invalid *catalog.ValidationError
	switch {
	case errors.As(err, &invalid):
		h.problem(w, r, kindValidation, detailValidation, toFieldErrors(invalid)...)
	case errors.Is(err, product.ErrNotFound):
		h.problem(w, r, kindNotFound, fmt.Sprintf(detailNotFound, id, market))
	case errors.Is(err, product.ErrVersionConflict):
		h.problem(w, r, kindPreconditionFailed, detailConflict)
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

func etag(version int64) string {
	return etagQuote + strconv.FormatInt(version, versionBase) + etagQuote
}

func toResponse(p product.Product) productResponse {
	return productResponse{
		ProductID:   string(p.ID),
		Name:        p.Name,
		SKU:         p.SKU,
		Status:      string(p.Status),
		TaxCategory: string(p.TaxCategory),
		Version:     p.Version,
	}
}

func toFieldErrors(invalid *catalog.ValidationError) []fieldError {
	fields := make([]fieldError, 0, len(invalid.Violations))
	for _, v := range invalid.Violations {
		fields = append(fields, fieldError{Field: v.Field, Message: v.Message})
	}
	return fields
}
