package auth

import (
	"context"
	"errors"
	"fmt"
	"slices"
	"time"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
)

const (
	signingAlgorithm = "RS256"
	clockLeeway      = 30 * time.Second
)

var (
	ErrUnauthenticated = errors.New("unauthenticated")
	ErrForbidden       = errors.New("forbidden")
	errMissingToken    = errors.New("missing bearer token")
	errMissingRole     = errors.New("required role not granted")
)

type KeySource interface {
	KeyfuncCtx(ctx context.Context) jwt.Keyfunc
}

type Settings struct {
	Issuer       string
	JWKSURL      string
	RequiredRole string
}

type Verifier struct {
	keys         KeySource
	parser       *jwt.Parser
	requiredRole string
}

type claims struct {
	jwt.RegisteredClaims
	RealmAccess realmAccess `json:"realm_access"`
}

type realmAccess struct {
	Roles []string `json:"roles"`
}

func NewJWKSVerifier(ctx context.Context, settings Settings) (*Verifier, error) {
	keys, err := keyfunc.NewDefaultCtx(ctx, []string{settings.JWKSURL})
	if err != nil {
		return nil, fmt.Errorf("create jwks key source: %w", err)
	}
	return NewVerifier(keys, settings), nil
}

func NewVerifier(keys KeySource, settings Settings) *Verifier {
	parser := jwt.NewParser(
		jwt.WithValidMethods([]string{signingAlgorithm}),
		jwt.WithIssuer(settings.Issuer),
		jwt.WithExpirationRequired(),
		jwt.WithLeeway(clockLeeway),
	)
	return &Verifier{keys: keys, parser: parser, requiredRole: settings.RequiredRole}
}

func (v *Verifier) Verify(ctx context.Context, token string) error {
	if token == "" {
		return fmt.Errorf("%w: %w", ErrUnauthenticated, errMissingToken)
	}
	var parsed claims
	if _, err := v.parser.ParseWithClaims(token, &parsed, v.keys.KeyfuncCtx(ctx)); err != nil {
		return fmt.Errorf("%w: %w", ErrUnauthenticated, err)
	}
	if !slices.Contains(parsed.RealmAccess.Roles, v.requiredRole) {
		return fmt.Errorf("%w: %w", ErrForbidden, errMissingRole)
	}
	return nil
}
