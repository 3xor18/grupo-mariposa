package catalog_test

import (
	"context"
	"errors"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/catalog"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

type stubRepository struct {
	found product.Product
	err   error
	calls int
}

func (s *stubRepository) FindByIDInMarket(
	_ context.Context, _ product.ID, _ product.Market,
) (product.Product, error) {
	s.calls++
	return s.found, s.err
}

func TestGetProductReturnsProduct(t *testing.T) {
	want := product.Product{ID: "PRD-001", Name: "Bebida", Status: product.StatusActive}
	repo := &stubRepository{found: want}
	got, err := catalog.NewService(repo).GetProduct(context.Background(),
		catalog.Query{ProductID: "PRD-001", Market: "MX"})
	if err != nil || got != want {
		t.Fatalf("want %+v, got %+v err=%v", want, got, err)
	}
}

func TestGetProductWrapsRepositoryError(t *testing.T) {
	repo := &stubRepository{err: product.ErrNotFound}
	_, err := catalog.NewService(repo).GetProduct(context.Background(),
		catalog.Query{ProductID: "PRD-001", Market: "PE"})
	if !errors.Is(err, product.ErrNotFound) {
		t.Fatalf("want ErrNotFound, got %v", err)
	}
}

func TestGetProductValidation(t *testing.T) {
	cases := []struct {
		name  string
		query catalog.Query
		want  []catalog.Violation
	}{
		{
			name:  "should_reject_invalid_id",
			query: catalog.Query{ProductID: "bad", Market: "MX"},
			want:  []catalog.Violation{{Field: catalog.FieldProductID}},
		},
		{
			name:  "should_require_market",
			query: catalog.Query{ProductID: "PRD-001"},
			want:  []catalog.Violation{{Field: catalog.FieldMarket, Message: "is required"}},
		},
		{
			name:  "should_report_all_violations",
			query: catalog.Query{ProductID: "x", Market: "US"},
			want: []catalog.Violation{
				{Field: catalog.FieldProductID},
				{Field: catalog.FieldMarket, Message: "must be one of MX, CO, PE"},
			},
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			repo := &stubRepository{}
			_, err := catalog.NewService(repo).GetProduct(context.Background(), tc.query)
			assertViolations(t, err, tc.want)
			if repo.calls != 0 {
				t.Fatal("repository must not be called on invalid input")
			}
		})
	}
}

func assertViolations(t *testing.T, err error, want []catalog.Violation) {
	t.Helper()
	var validationErr *catalog.ValidationError
	if !errors.As(err, &validationErr) {
		t.Fatalf("want ValidationError, got %v", err)
	}
	if len(validationErr.Violations) != len(want) || validationErr.Error() == "" {
		t.Fatalf("want %d violations, got %+v", len(want), validationErr.Violations)
	}
	for i, v := range want {
		got := validationErr.Violations[i]
		if got.Field != v.Field || (v.Message != "" && got.Message != v.Message) {
			t.Fatalf("violation %d: want %+v, got %+v", i, v, got)
		}
	}
}
