package mongodb

import (
	"context"
	"errors"
	"fmt"
	"time"

	"go.mongodb.org/mongo-driver/v2/bson"
	"go.mongodb.org/mongo-driver/v2/mongo"
	"go.mongodb.org/mongo-driver/v2/mongo/options"

	"github.com/grupomariposa/platform/products-api/internal/outbox"
	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	statusPending   = "PENDING"
	statusInFlight  = "IN_FLIGHT"
	statusPublished = "PUBLISHED"
	singleDocument  = 1
)

type outboxDocument struct {
	ID          string     `bson:"_id"`
	Topic       string     `bson:"topic"`
	Key         string     `bson:"key"`
	Payload     string     `bson:"payload"`
	ProductID   string     `bson:"productId"`
	Market      string     `bson:"market"`
	Version     int64      `bson:"version"`
	Status      string     `bson:"status"`
	Attempts    int        `bson:"attempts"`
	LeaseOwner  string     `bson:"leaseOwner,omitempty"`
	LeaseUntil  *time.Time `bson:"leaseUntil,omitempty"`
	AvailableAt time.Time  `bson:"availableAt"`
	CreatedAt   time.Time  `bson:"createdAt"`
	PublishedAt *time.Time `bson:"publishedAt,omitempty"`
}

func newOutboxDocument(event product.ChangeEvent, p product.Product, topic string,
) outboxDocument {
	createdAt := event.OccurredAt.UTC()
	return outboxDocument{
		ID:          event.ID,
		Topic:       topic,
		Key:         event.Key,
		Payload:     string(event.Payload),
		ProductID:   string(p.ID),
		Market:      string(p.Market),
		Version:     p.Version,
		Status:      statusPending,
		AvailableAt: createdAt,
		CreatedAt:   createdAt,
	}
}

func (s *Store) Claim(ctx context.Context, claim outbox.Claim) ([]outbox.PendingEvent, error) {
	candidates, err := s.candidates(ctx, claim)
	if err != nil {
		return nil, err
	}
	claimed := make([]outbox.PendingEvent, 0, len(candidates))
	for _, candidate := range candidates {
		leased, err := s.lease(ctx, candidate, claim)
		if err != nil {
			return claimed, err
		}
		claimed = append(claimed, leased...)
	}
	return claimed, nil
}

func (s *Store) candidates(ctx context.Context, claim outbox.Claim) ([]outboxDocument, error) {
	cursor, err := s.outbox.Aggregate(ctx, mongo.Pipeline{
		{{Key: "$match", Value: bson.M{fieldStatus: bson.M{opIn: unpublished()}}}},
		{{Key: "$sort", Value: bson.D{{Key: fieldKey, Value: ascending},
			{Key: fieldVersion, Value: ascending}}}},
		{{Key: "$group", Value: bson.M{fieldID: "$" + fieldKey,
			"oldest": bson.M{"$first": "$$ROOT"}}}},
		{{Key: "$replaceWith", Value: "$oldest"}},
		{{Key: "$match", Value: claimable(claim.Now)}},
		{{Key: "$sort", Value: bson.D{{Key: fieldCreatedAt, Value: ascending}}}},
		{{Key: "$limit", Value: claim.Limit}},
	})
	if err != nil {
		return nil, fmt.Errorf("find outbox candidates: %w", err)
	}
	var candidates []outboxDocument
	return candidates, wrap("decode outbox candidates", cursor.All(ctx, &candidates))
}

func (s *Store) lease(ctx context.Context, candidate outboxDocument, claim outbox.Claim,
) ([]outbox.PendingEvent, error) {
	filter := bson.M{fieldID: candidate.ID, opOr: claimable(claim.Now)[opOr]}
	update := bson.M{opSet: bson.M{fieldStatus: statusInFlight,
		fieldLeaseUntil: claim.LeaseUntil.UTC(), fieldLeaseOwner: claim.Owner}}
	var leased outboxDocument
	err := s.outbox.FindOneAndUpdate(ctx, filter, update,
		options.FindOneAndUpdate().SetReturnDocument(options.After)).Decode(&leased)
	switch {
	case err == nil:
		return []outbox.PendingEvent{toPending(leased)}, nil
	case isNoDocuments(err):
		return nil, nil
	default:
		return nil, fmt.Errorf("lease outbox event %s: %w", candidate.ID, err)
	}
}

func (s *Store) MarkPublished(ctx context.Context, id, owner string, at time.Time,
) (bool, error) {
	result, err := s.outbox.UpdateOne(ctx, heldBy(id, owner), bson.M{
		opSet:   bson.M{fieldStatus: statusPublished, fieldPublishedAt: at.UTC()},
		opUnset: bson.M{fieldLeaseUntil: "", fieldLeaseOwner: ""},
	})
	return modifiedOne(result, wrap("mark outbox event published", err))
}

func (s *Store) Release(ctx context.Context, id, owner string, attempts int,
	availableAt time.Time,
) (bool, error) {
	result, err := s.outbox.UpdateOne(ctx, heldBy(id, owner), bson.M{
		opSet: bson.M{fieldStatus: statusPending, fieldAttempts: attempts,
			fieldAvailableAt: availableAt.UTC()},
		opUnset: bson.M{fieldLeaseUntil: "", fieldLeaseOwner: ""},
	})
	return modifiedOne(result, wrap("release outbox event", err))
}

func (s *Store) CountUnpublished(ctx context.Context) (int64, error) {
	count, err := s.outbox.CountDocuments(ctx, bson.M{fieldStatus: bson.M{opIn: unpublished()}})
	return count, wrap("count unpublished outbox events", err)
}

func unpublished() []string {
	return []string{statusPending, statusInFlight}
}

func claimable(now time.Time) bson.M {
	return bson.M{opOr: bson.A{
		bson.M{fieldStatus: statusPending, fieldAvailableAt: bson.M{opLessOrEqual: now.UTC()}},
		bson.M{fieldStatus: statusInFlight, fieldLeaseUntil: bson.M{opLessThan: now.UTC()}},
	}}
}

func heldBy(id, owner string) bson.M {
	return bson.M{fieldID: id, fieldLeaseOwner: owner, fieldStatus: statusInFlight}
}

func modifiedOne(result *mongo.UpdateResult, err error) (bool, error) {
	if err != nil {
		return false, err
	}
	return result.ModifiedCount == singleDocument, nil
}

func isNoDocuments(err error) bool {
	return errors.Is(err, mongo.ErrNoDocuments)
}

func toPending(doc outboxDocument) outbox.PendingEvent {
	return outbox.PendingEvent{ID: doc.ID, Key: doc.Key, Payload: []byte(doc.Payload),
		Attempts: doc.Attempts}
}
