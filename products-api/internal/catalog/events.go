package catalog

import (
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

const occurredAtLayout = "2006-01-02T15:04:05.000Z07:00"

type productChanged struct {
	EventID     string `json:"eventId"`
	OccurredAt  string `json:"occurredAt"`
	ProductID   string `json:"productId"`
	Market      string `json:"market"`
	Version     int64  `json:"version"`
	Status      string `json:"status"`
	TaxCategory string `json:"taxCategory"`
	Name        string `json:"name"`
	SKU         string `json:"sku"`
}

type ChangeEvents struct {
	clock func() time.Time
	newID func() (string, error)
}

func NewChangeEvents(clock func() time.Time, newID func() (string, error)) *ChangeEvents {
	return &ChangeEvents{clock: clock, newID: newID}
}

func (f *ChangeEvents) Build(p product.Product) (product.ChangeEvent, error) {
	id, idErr := f.newID()
	occurredAt := f.clock().UTC()
	payload, encodeErr := json.Marshal(productChanged{
		EventID:     id,
		OccurredAt:  occurredAt.Format(occurredAtLayout),
		ProductID:   string(p.ID),
		Market:      string(p.Market),
		Version:     p.Version,
		Status:      string(p.Status),
		TaxCategory: string(p.TaxCategory),
		Name:        p.Name,
		SKU:         p.SKU,
	})
	if err := errors.Join(idErr, encodeErr); err != nil {
		return product.ChangeEvent{}, fmt.Errorf("build change event: %w", err)
	}
	return product.ChangeEvent{ID: id, Key: p.EventKey(), OccurredAt: occurredAt,
		Payload: payload}, nil
}
