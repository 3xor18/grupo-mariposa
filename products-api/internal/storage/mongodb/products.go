package mongodb

import (
	"context"
	"errors"
	"fmt"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

type productDocument struct {
	ProductID   string    `bson:"productId"`
	Market      string    `bson:"market"`
	Name        string    `bson:"name"`
	SKU         string    `bson:"sku"`
	Status      string    `bson:"status"`
	TaxCategory string    `bson:"taxCategory"`
	Version     int64     `bson:"version"`
	UpdatedAt   time.Time `bson:"updatedAt"`
}

func toDocument(p product.Product, updatedAt time.Time) productDocument {
	return productDocument{
		ProductID:   string(p.ID),
		Market:      string(p.Market),
		Name:        p.Name,
		SKU:         p.SKU,
		Status:      string(p.Status),
		TaxCategory: string(p.TaxCategory),
		Version:     p.Version,
		UpdatedAt:   updatedAt,
	}
}

func (d productDocument) toDomain() product.Product {
	return product.Product{
		ID:          product.ID(d.ProductID),
		Market:      product.Market(d.Market),
		Name:        d.Name,
		SKU:         d.SKU,
		Status:      product.Status(d.Status),
		TaxCategory: product.TaxCategory(d.TaxCategory),
		Version:     d.Version,
	}
}

func (s *Store) FindByIDInMarket(
	ctx context.Context, id product.ID, market product.Market,
) (product.Product, error) {
	var doc productDocument
	err := s.products.FindOne(ctx, byKey(id, market)).Decode(&doc)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return product.Product{}, product.ErrNotFound
	}
	if err != nil {
		return product.Product{}, fmt.Errorf("find product %s in %s: %w", id, market, err)
	}
	return doc.toDomain(), nil
}

func (s *Store) Update(ctx context.Context, request product.UpdateRequest,
) (product.Product, error) {
	var updated product.Product
	err := s.client.UseSession(ctx, func(sessionCtx context.Context) error {
		session := mongo.SessionFromContext(sessionCtx)
		_, txErr := session.WithTransaction(sessionCtx, func(txCtx context.Context) (any, error) {
			var err error
			updated, err = s.updateInTransaction(txCtx, request)
			return nil, err
		})
		return txErr
	})
	if err != nil {
		return product.Product{}, err
	}
	return updated, nil
}

func (s *Store) updateInTransaction(ctx context.Context, request product.UpdateRequest,
) (product.Product, error) {
	current, err := s.FindByIDInMarket(ctx, request.ID, request.Market)
	if err != nil {
		return product.Product{}, err
	}
	if !request.Matches(current) {
		return product.Product{}, product.ErrVersionConflict
	}
	updated, changed := current.Apply(request.Patch)
	if !changed {
		return current, nil
	}
	event, err := request.NewEvent(updated)
	if err != nil {
		return product.Product{}, fmt.Errorf("build change event: %w", err)
	}
	result, err := s.products.UpdateOne(ctx, versioned(current), changes(updated, event))
	if err := requireMatched(result, err); err != nil {
		return product.Product{}, err
	}
	_, err = s.outbox.InsertOne(ctx, newOutboxDocument(event, updated, s.topic))
	return updated, wrap("insert outbox event", err)
}

func versioned(current product.Product) bson.M {
	filter := byKey(current.ID, current.Market)
	filter[fieldVersion] = current.Version
	return filter
}

func changes(updated product.Product, event product.ChangeEvent) bson.M {
	return bson.M{opSet: bson.M{
		fieldName:        updated.Name,
		fieldStatus:      string(updated.Status),
		fieldTaxCategory: string(updated.TaxCategory),
		fieldVersion:     updated.Version,
		fieldUpdatedAt:   event.OccurredAt.UTC(),
	}}
}

func requireMatched(result *mongo.UpdateResult, err error) error {
	if err != nil {
		return fmt.Errorf("update product: %w", err)
	}
	if result.MatchedCount == 0 {
		return product.ErrVersionConflict
	}
	return nil
}
