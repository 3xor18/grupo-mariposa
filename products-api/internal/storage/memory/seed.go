package memory

import "github.com/grupomariposa/platform/products-api/internal/product"

func Seed() []Listing {
	return []Listing{
		listed("PRD-001", "Bebida 600 ml", "BEB-600-PET", product.StatusActive, product.TaxStandard,
			product.MarketMX, product.MarketCO, product.MarketPE),
		mx("PRD-002", "Agua natural 1 L", "AGU-1000-PET", product.StatusActive, product.TaxExempt),
		mx("PRD-003", "Galletas surtidas 200 g", "GAL-200-SUR",
			product.StatusActive, product.TaxReduced),
		mx("PRD-004", "Refresco retornable 355 ml", "REF-355-VID",
			product.StatusDiscontinued, product.TaxStandard),
		mx("PRD-008", "Jugo de naranja 1 L", "JUG-1000-NAR", product.StatusActive, product.TaxStandard),
		mx("PRD-012", "Café molido 500 g", "CAF-500-MOL", product.StatusActive, product.TaxReduced),
		mx("PRD-013", "Té verde 20 sobres", "TEV-020-SOB", product.StatusActive, product.TaxReduced),
		co("PRD-005", "Arepa precocida 1 kg", "ARE-1000-PRE", product.StatusActive, product.TaxReduced),
		co("PRD-006", "Café tostado 250 g", "CAF-250-TOS", product.StatusActive, product.TaxStandard),
		co("PRD-007", "Panela 500 g", "PAN-500-BLQ", product.StatusDiscontinued, product.TaxExempt),
		pe("PRD-009", "Quinua 1 kg", "QUI-1000-BLA", product.StatusActive, product.TaxExempt),
		pe("PRD-010", "Chocolate 90 g", "CHO-090-BAR", product.StatusActive, product.TaxReduced),
		pe("PRD-011", "Galleta de soda 6 un", "GAL-006-SOD", product.StatusActive, product.TaxStandard),
		pe("PRD-014", "Aceite vegetal 1 L", "ACE-1000-VEG", product.StatusActive, product.TaxStandard),
	}
}

func mx(id, name, sku string, status product.Status, tax product.TaxCategory) Listing {
	return listed(id, name, sku, status, tax, product.MarketMX)
}

func co(id, name, sku string, status product.Status, tax product.TaxCategory) Listing {
	return listed(id, name, sku, status, tax, product.MarketCO)
}

func pe(id, name, sku string, status product.Status, tax product.TaxCategory) Listing {
	return listed(id, name, sku, status, tax, product.MarketPE)
}

func listed(
	id, name, sku string, status product.Status, tax product.TaxCategory, markets ...product.Market,
) Listing {
	return Listing{
		Product: product.Product{
			ID: product.ID(id), Name: name, SKU: sku, Status: status, TaxCategory: tax,
		},
		Markets: markets,
	}
}
