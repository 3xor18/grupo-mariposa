package memory

import (
	"context"
	"fmt"
	"sync"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

type key struct {
	id     product.ID
	market product.Market
}

type Repository struct {
	mu       sync.RWMutex
	products map[key]product.Product
}

func NewRepository(products []product.Product) *Repository {
	indexed := make(map[key]product.Product, len(products))
	for _, p := range products {
		indexed[key{id: p.ID, market: p.Market}] = p
	}
	return &Repository{products: indexed}
}

func (r *Repository) FindByIDInMarket(
	ctx context.Context, id product.ID, market product.Market,
) (product.Product, error) {
	if err := ctx.Err(); err != nil {
		return product.Product{}, fmt.Errorf("find product %s: %w", id, err)
	}
	r.mu.RLock()
	defer r.mu.RUnlock()
	found, ok := r.products[key{id: id, market: market}]
	if !ok {
		return product.Product{}, product.ErrNotFound
	}
	return found, nil
}

func (r *Repository) Update(ctx context.Context, request product.UpdateRequest,
) (product.Product, error) {
	if err := ctx.Err(); err != nil {
		return product.Product{}, fmt.Errorf("update product %s: %w", request.ID, err)
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	k := key{id: request.ID, market: request.Market}
	current, ok := r.products[k]
	if !ok {
		return product.Product{}, product.ErrNotFound
	}
	if !request.Matches(current) {
		return product.Product{}, product.ErrVersionConflict
	}
	updated := current.Apply(request.Patch)
	if _, err := request.NewEvent(updated); err != nil {
		return product.Product{}, fmt.Errorf("build change event: %w", err)
	}
	r.products[k] = updated
	return updated, nil
}
