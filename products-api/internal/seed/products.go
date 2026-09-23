package seed

import "github.com/grupomariposa/platform/products-api/internal/product"

const (
	mx product.Market = "MX"
	co product.Market = "CO"
	pe product.Market = "PE"
	cl product.Market = "CL"
	ec product.Market = "EC"
)

type row struct {
	id      product.ID
	name    string
	sku     string
	status  product.Status
	tax     product.TaxCategory
	markets []product.Market
}

func Products() []product.Product {
	var products []product.Product
	for _, r := range rows() {
		for _, m := range r.markets {
			products = append(products, product.Product{
				ID: r.id, Market: m, Name: r.name, SKU: r.sku, Status: r.status,
				TaxCategory: r.tax, Version: product.InitialVersion,
			})
		}
	}
	return products
}

func rows() []row {
	active, discontinued := product.StatusActive, product.StatusDiscontinued
	standard, reduced, exempt := product.TaxStandard, product.TaxReduced, product.TaxExempt
	return []row{
		{"PRD-001", "Bebida 600 ml", "BEB-600-PET", active, standard,
			[]product.Market{mx, co, pe, cl, ec}},
		{"PRD-002", "Agua natural 1 L", "AGU-1000-PET", active, exempt, []product.Market{mx}},
		{"PRD-003", "Galletas surtidas 200 g", "GAL-200-SUR", active, reduced, []product.Market{mx}},
		{"PRD-004", "Refresco retornable 355 ml", "REF-355-VID", discontinued, standard,
			[]product.Market{mx}},
		{"PRD-008", "Jugo de naranja 1 L", "JUG-1000-NAR", active, standard, []product.Market{mx}},
		{"PRD-012", "Café molido 500 g", "CAF-500-MOL", active, reduced, []product.Market{mx}},
		{"PRD-013", "Té verde 20 sobres", "TEV-020-SOB", active, reduced, []product.Market{mx}},
		{"PRD-020", "Producto demo caché", "DEM-001-CCH", active, standard, []product.Market{mx}},
		{"PRD-005", "Arepa precocida 1 kg", "ARE-1000-PRE", active, reduced, []product.Market{co}},
		{"PRD-006", "Café tostado 250 g", "CAF-250-TOS", active, standard, []product.Market{co}},
		{"PRD-007", "Panela 500 g", "PAN-500-BLQ", discontinued, exempt, []product.Market{co}},
		{"PRD-009", "Quinua 1 kg", "QUI-1000-BLA", active, exempt, []product.Market{pe}},
		{"PRD-010", "Chocolate 90 g", "CHO-090-BAR", active, reduced, []product.Market{pe}},
		{"PRD-011", "Galleta de soda 6 un", "GAL-006-SOD", active, standard, []product.Market{pe}},
		{"PRD-014", "Aceite vegetal 1 L", "ACE-1000-VEG", active, standard, []product.Market{pe}},
		{"PRD-015", "Vino tinto 750 ml", "VIN-750-TIN", active, standard, []product.Market{cl}},
		{"PRD-016", "Harina de trigo 1 kg", "HAR-1000-TRI", active, reduced, []product.Market{cl}},
		{"PRD-017", "Pan de molde 500 g", "PAN-500-MOL", discontinued, standard,
			[]product.Market{cl}},
		{"PRD-018", "Banano 1 kg", "BAN-1000-CAV", active, exempt, []product.Market{ec}},
		{"PRD-019", "Cemento 50 kg", "CEM-050-GRI", active, reduced, []product.Market{ec}},
	}
}
