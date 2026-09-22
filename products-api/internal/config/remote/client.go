package remote

import (
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"time"
)

const (
	propertiesPathFormat = "%s/%s-%s.properties"
	acceptHeader         = "Accept"
	acceptProperties     = "text/plain"
	maxBodyBytes         = 1 << 20
	backoffFactor        = 2
)

var (
	ErrUnavailable     = errors.New("config server unavailable")
	errUnexpectedReply = errors.New("unexpected config server status")
)

type Client struct {
	settings Settings
	http     *http.Client
}

func NewClient(settings Settings) *Client {
	return &Client{settings: settings, http: &http.Client{Timeout: settings.Timeout}}
}

func (c *Client) Fetch(ctx context.Context) (map[string]string, error) {
	var lastErr error
	delay := c.settings.Backoff
	for attempt := 0; attempt <= c.settings.Retries; attempt++ {
		if attempt > 0 {
			if err := sleep(ctx, delay); err != nil {
				return nil, fmt.Errorf("%w: %w", ErrUnavailable, err)
			}
			delay *= backoffFactor
		}
		properties, retryable, err := c.fetchOnce(ctx)
		if err == nil {
			return properties, nil
		}
		lastErr = err
		if !retryable {
			break
		}
	}
	return nil, fmt.Errorf("%w: %w", ErrUnavailable, lastErr)
}

func (c *Client) fetchOnce(ctx context.Context) (map[string]string, bool, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.propertiesURL(), nil)
	if err != nil {
		return nil, false, fmt.Errorf("build request: %w", err)
	}
	req.Header.Set(acceptHeader, acceptProperties)
	if c.settings.Username != "" {
		req.SetBasicAuth(c.settings.Username, c.settings.Password)
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return nil, true, fmt.Errorf("request properties: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, retryableStatus(resp.StatusCode),
			fmt.Errorf("%w: %d", errUnexpectedReply, resp.StatusCode)
	}
	properties, err := ParseProperties(io.LimitReader(resp.Body, maxBodyBytes))
	return properties, false, err
}

func (c *Client) propertiesURL() string {
	s := c.settings
	return fmt.Sprintf(propertiesPathFormat, s.BaseURL, s.AppName, s.Profile)
}

func retryableStatus(status int) bool {
	return status == http.StatusTooManyRequests || status >= http.StatusInternalServerError
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
