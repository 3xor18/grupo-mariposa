package mongodb

import (
	"context"
	"fmt"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"
	"go.mongodb.org/mongo-driver/v2/mongo/readpref"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	productsCollection = "products"
	outboxCollection   = "outbox"
	fieldID            = "_id"
	fieldProductID     = "productId"
	fieldMarket        = "market"
	fieldName          = "name"
	fieldSKU           = "sku"
	fieldStatus        = "status"
	fieldTaxCategory   = "taxCategory"
	fieldVersion       = "version"
	fieldUpdatedAt     = "updatedAt"
	fieldKey           = "key"
	fieldAvailableAt   = "availableAt"
	fieldLeaseUntil    = "leaseUntil"
	fieldLeaseOwner    = "leaseOwner"
	fieldCreatedAt     = "createdAt"
	fieldPublishedAt   = "publishedAt"
	fieldAttempts      = "attempts"
	opSet              = "$set"
	opSetOnInsert      = "$setOnInsert"
	opUnset            = "$unset"
	opIn               = "$in"
	opLessThan         = "$lt"
	opLessOrEqual      = "$lte"
	opOr               = "$or"
	ascending          = 1
)

type Settings struct {
	URI      string
	Database string
	Topic    string
	Timeout  time.Duration
}

type Store struct {
	client   *mongo.Client
	products *mongo.Collection
	outbox   *mongo.Collection
	topic    string
}

type index struct {
	collection *mongo.Collection
	model      mongo.IndexModel
}

func Open(settings Settings) (*Store, error) {
	client, err := mongo.Connect(options.Client().
		ApplyURI(settings.URI).
		SetTimeout(settings.Timeout).
		SetServerSelectionTimeout(settings.Timeout))
	if err != nil {
		return nil, fmt.Errorf("connect mongodb: %w", err)
	}
	db := client.Database(settings.Database)
	return &Store{
		client:   client,
		products: db.Collection(productsCollection),
		outbox:   db.Collection(outboxCollection),
		topic:    settings.Topic,
	}, nil
}

func (s *Store) Close(ctx context.Context) error {
	return wrap("disconnect mongodb", s.client.Disconnect(ctx))
}

func (s *Store) Ping(ctx context.Context) error {
	return wrap("ping mongodb", s.client.Ping(ctx, readpref.Primary()))
}

func (s *Store) Setup(ctx context.Context, seed []product.Product) error {
	for _, ix := range s.indexes() {
		if _, err := ix.collection.Indexes().CreateOne(ctx, ix.model); err != nil {
			return fmt.Errorf("create index on %s: %w", ix.collection.Name(), err)
		}
	}
	return s.seed(ctx, seed)
}

func (s *Store) indexes() []index {
	return []index{
		{s.products, mongo.IndexModel{
			Keys:    bson.D{{Key: fieldProductID, Value: ascending}, {Key: fieldMarket, Value: ascending}},
			Options: options.Index().SetUnique(true),
		}},
		{s.outbox, mongo.IndexModel{Keys: bson.D{{Key: fieldStatus, Value: ascending},
			{Key: fieldAvailableAt, Value: ascending}}}},
		{s.outbox, mongo.IndexModel{Keys: bson.D{{Key: fieldStatus, Value: ascending},
			{Key: fieldLeaseUntil, Value: ascending}}}},
		{s.outbox, mongo.IndexModel{Keys: bson.D{{Key: fieldKey, Value: ascending},
			{Key: fieldVersion, Value: ascending}}}},
	}
}

func (s *Store) seed(ctx context.Context, seed []product.Product) error {
	models := make([]mongo.WriteModel, 0, len(seed))
	for _, p := range seed {
		models = append(models, mongo.NewUpdateOneModel().
			SetFilter(byKey(p.ID, p.Market)).
			SetUpdate(bson.M{opSetOnInsert: toDocument(p, time.Now().UTC())}).
			SetUpsert(true))
	}
	_, err := s.products.BulkWrite(ctx, models, options.BulkWrite().SetOrdered(false))
	return wrap("seed products", err)
}

func byKey(id product.ID, market product.Market) bson.M {
	return bson.M{fieldProductID: string(id), fieldMarket: string(market)}
}

func wrap(operation string, err error) error {
	if err == nil {
		return nil
	}
	return fmt.Errorf("%s: %w", operation, err)
}
