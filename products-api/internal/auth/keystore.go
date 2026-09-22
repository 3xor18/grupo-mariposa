package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"sync/atomic"
	"time"

	"github.com/MicahParks/jwkset"
	"github.com/golang-jwt/jwt/v5"
	"golang.org/x/time/rate"
)

const (
	logRefreshFailed = "jwks refresh failed"
	logKeyError      = "error"
	headerKeyID      = "kid"
	refreshBurst     = 1
)

var (
	errJWKSStatus        = errors.New("unexpected jwks status")
	errMissingKeyID      = errors.New("token header has no kid")
	errAlgorithmMismatch = errors.New("token alg does not match signing key")
)

type keyStorage interface {
	KeyRead(ctx context.Context, keyID string) (jwkset.JWK, error)
	KeyReplaceAll(ctx context.Context, keys []jwkset.JWK) error
}

type keyStore struct {
	store   keyStorage
	url     string
	client  *http.Client
	limiter *rate.Limiter
	warmed  atomic.Bool
	logger  *slog.Logger
}

func newKeyStore(url string, timeout, minimumRefresh time.Duration, logger *slog.Logger) *keyStore {
	return &keyStore{
		store:   jwkset.NewMemoryStorage(),
		url:     url,
		client:  &http.Client{Timeout: timeout},
		limiter: rate.NewLimiter(rate.Every(minimumRefresh), refreshBurst),
		logger:  logger,
	}
}

func (s *keyStore) keyfunc(ctx context.Context) jwt.Keyfunc {
	return func(token *jwt.Token) (any, error) {
		keyID, _ := token.Header[headerKeyID].(string)
		if keyID == "" {
			return nil, errMissingKeyID
		}
		jwk, err := s.find(ctx, keyID)
		if err != nil {
			return nil, err
		}
		if alg := jwk.Marshal().ALG.String(); alg != "" && alg != token.Method.Alg() {
			return nil, errAlgorithmMismatch
		}
		return jwk.Key(), nil
	}
}

func (s *keyStore) find(ctx context.Context, keyID string) (jwkset.JWK, error) {
	jwk, err := s.store.KeyRead(ctx, keyID)
	if err == nil {
		return jwk, nil
	}
	if err := s.refreshIfAllowed(ctx); err != nil {
		return jwkset.JWK{}, err
	}
	return s.store.KeyRead(ctx, keyID)
}

func (s *keyStore) refreshIfAllowed(ctx context.Context) error {
	if !s.limiter.Allow() {
		if s.warmed.Load() {
			return nil
		}
		return fmt.Errorf("%w: signing keys not loaded yet", ErrUnavailable)
	}
	if err := s.refresh(ctx); err != nil {
		return fmt.Errorf("%w: %w", ErrUnavailable, err)
	}
	return nil
}

func (s *keyStore) refresh(ctx context.Context) error {
	set, err := s.download(ctx)
	if err != nil {
		return err
	}
	keys := make([]jwkset.JWK, 0, len(set.Keys))
	for _, marshal := range set.Keys {
		jwk, err := jwkset.NewJWKFromMarshal(marshal, jwkset.JWKMarshalOptions{},
			jwkset.JWKValidateOptions{})
		if err == nil {
			keys = append(keys, jwk)
		}
	}
	if err := s.store.KeyReplaceAll(ctx, keys); err != nil {
		return fmt.Errorf("store jwks: %w", err)
	}
	s.warmed.Store(true)
	return nil
}

func (s *keyStore) download(ctx context.Context) (jwkset.JWKSMarshal, error) {
	var set jwkset.JWKSMarshal
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.url, nil)
	if err != nil {
		return set, fmt.Errorf("build jwks request: %w", err)
	}
	resp, err := s.client.Do(req)
	if err != nil {
		return set, fmt.Errorf("fetch jwks: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return set, fmt.Errorf("%w: %d", errJWKSStatus, resp.StatusCode)
	}
	if err := json.NewDecoder(resp.Body).Decode(&set); err != nil {
		return set, fmt.Errorf("decode jwks: %w", err)
	}
	return set, nil
}

func (s *keyStore) run(ctx context.Context, every, retry time.Duration) {
	for {
		wait := every
		if err := s.refresh(ctx); err != nil {
			s.logger.WarnContext(ctx, logRefreshFailed, logKeyError, err.Error())
			wait = retry
		}
		if !sleep(ctx, wait) {
			return
		}
	}
}

func sleep(ctx context.Context, wait time.Duration) bool {
	timer := time.NewTimer(wait)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-timer.C:
		return true
	}
}
