package remote

import (
	"bytes"
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"io"
	"math/big"
	"mime"
	"net/http"
	"net/url"
	"time"
)

const (
	propertiesPathFormat = "%s/%s-%s.properties"
	acceptHeader         = "Accept"
	contentTypeHeader    = "Content-Type"
	mediaTypeProperties  = "text/plain"
	defaultMaxBodyBytes  = 1 << 20
	backoffFactor        = 2
	jitterDivisor        = 2
)

var (
	ErrUnavailable      = errors.New("config server unavailable")
	errUnexpectedStatus = errors.New("unexpected config server status")
	errUnexpectedMedia  = errors.New("unexpected config server content type")
	errBodyTooLarge     = errors.New("config server response too large")
	errPermanentFailure = errors.New("permanent config server failure")
	errTransientFailure = errors.New("transient config server failure")
)

type client struct {
	settings settings
	http     *http.Client
	maxBytes int64
}

func newClient(s settings) *client {
	return &client{settings: s, http: &http.Client{Timeout: s.timeout}, maxBytes: defaultMaxBodyBytes}
}

func (c *client) fetch(ctx context.Context) (map[string]string, error) {
	var lastErr error
	delay := c.settings.backoff
	for attempt := 0; attempt <= c.settings.retries; attempt++ {
		if attempt > 0 {
			if err := sleep(ctx, jitter(delay)); err != nil {
				return nil, fmt.Errorf("%w: %w", ErrUnavailable, err)
			}
			delay = min(delay*backoffFactor, c.settings.backoffMax)
		}
		properties, err := c.fetchOnce(ctx)
		if err == nil {
			return properties, nil
		}
		lastErr = err
		if errors.Is(err, errPermanentFailure) {
			break
		}
	}
	return nil, fmt.Errorf("%w: %w", ErrUnavailable, lastErr)
}

func (c *client) fetchOnce(ctx context.Context) (map[string]string, error) {
	req, err := c.newRequest(ctx)
	if err != nil {
		return nil, err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", errTransientFailure, err)
	}
	defer func() { _ = resp.Body.Close() }()
	if err := checkResponse(resp); err != nil {
		return nil, err
	}
	body, err := c.readBody(resp.Body)
	if err != nil {
		return nil, err
	}
	properties, err := parseProperties(bytes.NewReader(body))
	if err != nil {
		return nil, fmt.Errorf("%w: %w", errPermanentFailure, err)
	}
	return properties, nil
}

func (c *client) newRequest(ctx context.Context) (*http.Request, error) {
	target := fmt.Sprintf(propertiesPathFormat, c.settings.baseURL,
		url.PathEscape(c.settings.appName), url.PathEscape(c.settings.profile))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
	if err != nil {
		return nil, fmt.Errorf("%w: build request: %w", errPermanentFailure, err)
	}
	req.Header.Set(acceptHeader, mediaTypeProperties)
	if c.settings.username != "" {
		req.SetBasicAuth(c.settings.username, c.settings.password)
	}
	return req, nil
}

func checkResponse(resp *http.Response) error {
	if resp.StatusCode != http.StatusOK {
		category := errPermanentFailure
		if retryableStatus(resp.StatusCode) {
			category = errTransientFailure
		}
		return fmt.Errorf("%w: %w: %d", category, errUnexpectedStatus, resp.StatusCode)
	}
	mediaType, _, err := mime.ParseMediaType(resp.Header.Get(contentTypeHeader))
	if err != nil || mediaType != mediaTypeProperties {
		return fmt.Errorf("%w: %w: %q", errPermanentFailure, errUnexpectedMedia,
			resp.Header.Get(contentTypeHeader))
	}
	return nil
}

func (c *client) readBody(body io.Reader) ([]byte, error) {
	data, err := io.ReadAll(io.LimitReader(body, c.maxBytes+1))
	if err != nil {
		return nil, fmt.Errorf("%w: read body: %w", errTransientFailure, err)
	}
	if int64(len(data)) > c.maxBytes {
		return nil, fmt.Errorf("%w: %w: over %d bytes", errPermanentFailure, errBodyTooLarge,
			c.maxBytes)
	}
	return data, nil
}

func retryableStatus(status int) bool {
	return status == http.StatusTooManyRequests || status >= http.StatusInternalServerError
}

func jitter(delay time.Duration) time.Duration {
	half := delay / jitterDivisor
	spread, _ := rand.Int(rand.Reader, big.NewInt(int64(half)+1))
	return half + time.Duration(spread.Int64())
}

func sleep(ctx context.Context, delay time.Duration) error {
	timer := time.NewTimer(delay)
	defer timer.Stop()
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-timer.C:
		return nil
	}
}
