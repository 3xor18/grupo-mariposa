package auth

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"slices"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

const signingAlgorithm = "RS256"

var (
	ErrUnauthenticated = errors.New("unauthenticated")
	ErrForbidden       = errors.New("forbidden")
	ErrUnavailable     = errors.New("authentication backend unavailable")
	errMissingToken    = errors.New("missing bearer token")
	errMissingRole     = errors.New("required role not granted")
	errMissingSubject  = errors.New("token has no subject")
)

type Settings struct {
	Issuer         string
	Audience       string
	JWKSURL        string
	RequiredRole   string
	ClockLeeway    time.Duration
	JWKSTimeout    time.Duration
	Refresh        time.Duration
	MinimumRefresh time.Duration
}

type Principal struct {
	ID string
}

type Verifier struct {
	keys         *keyStore
	parser       *jwt.Parser
	requiredRole string
	refresh      time.Duration
	retry        time.Duration
}

type claims struct {
	jwt.RegisteredClaims
	AuthorizedParty string      `json:"azp"`
	RealmAccess     realmAccess `json:"realm_access"`
}

type realmAccess struct {
	Roles []string `json:"roles"`
}

func NewVerifier(settings Settings, logger *slog.Logger) *Verifier {
	return &Verifier{
		keys: newKeyStore(settings.JWKSURL, settings.JWKSTimeout, settings.MinimumRefresh,
			logger),
		parser:       jwt.NewParser(parserOptions(settings)...),
		requiredRole: settings.RequiredRole,
		refresh:      settings.Refresh,
		retry:        settings.MinimumRefresh,
	}
}

func parserOptions(settings Settings) []jwt.ParserOption {
	options := []jwt.ParserOption{
		jwt.WithValidMethods([]string{signingAlgorithm}),
		jwt.WithIssuer(settings.Issuer),
		jwt.WithExpirationRequired(),
		jwt.WithLeeway(settings.ClockLeeway),
	}
	if settings.Audience != "" {
		options = append(options, jwt.WithAudience(settings.Audience))
	}
	return options
}

func (v *Verifier) Run(ctx context.Context) {
	v.keys.run(ctx, v.refresh, v.retry)
}

func (v *Verifier) Ready() bool {
	return v.keys.warmed.Load()
}

func (v *Verifier) Verify(ctx context.Context, token string) (Principal, error) {
	if token == "" {
		return Principal{}, fmt.Errorf("%w: %w", ErrUnauthenticated, errMissingToken)
	}
	var parsed claims
	_, err := v.parser.ParseWithClaims(token, &parsed, v.keys.keyfunc(ctx))
	switch {
	case errors.Is(err, ErrUnavailable), err != nil && ctx.Err() != nil:
		return Principal{}, fmt.Errorf("%w: %w", ErrUnavailable, err)
	case err != nil:
		return Principal{}, fmt.Errorf("%w: %w", ErrUnauthenticated, err)
	}
	return authorize(parsed, v.requiredRole)
}

func authorize(parsed claims, requiredRole string) (Principal, error) {
	id := parsed.AuthorizedParty
	if id == "" {
		id = parsed.Subject
	}
	if id == "" {
		return Principal{}, fmt.Errorf("%w: %w", ErrUnauthenticated, errMissingSubject)
	}
	if !slices.Contains(parsed.RealmAccess.Roles, requiredRole) {
		return Principal{}, fmt.Errorf("%w: %w", ErrForbidden, errMissingRole)
	}
	return Principal{ID: id}, nil
}
