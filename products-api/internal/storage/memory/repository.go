package memory

import (
	"context"
	"fmt"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

type listing struct {
	Product product.Product
	Markets []product.Market
}

type Repository struct {
	listings map[product.ID]entry
}

type entry struct {
	product product.Product
	markets map[product.Market]struct{}
}

func newRepository(listings []listing) *Repository {
	indexed := make(map[product.ID]entry, len(listings))
	for _, l := range listings {
		indexed[l.Product.ID] = entry{product: l.Product, markets: toSet(l.Markets)}
	}
	return &Repository{listings: indexed}
}

func NewSeededRepository() *Repository {
	return newRepository(seed())
}

func (r *Repository) FindByIDInMarket(
	ctx context.Context, id product.ID, market product.Market,
) (product.Product, error) {
	if err := ctx.Err(); err != nil {
		return product.Product{}, fmt.Errorf("find product %s: %w", id, err)
	}
	found, ok := r.listings[id]
	if !ok {
		return product.Product{}, product.ErrNotFound
	}
	if _, available := found.markets[market]; !available {
		return product.Product{}, product.ErrNotFound
	}
	return found.product, nil
}

func toSet(markets []product.Market) map[product.Market]struct{} {
	set := make(map[product.Market]struct{}, len(markets))
	for _, m := range markets {
		set[m] = struct{}{}
	}
	return set
}
