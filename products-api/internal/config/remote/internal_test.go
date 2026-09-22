package remote

import "testing"

func TestUnescapeKeepsLoneTrailingBackslash(t *testing.T) {
	got, err := unescape([]rune(`end\`))
	if err != nil || got != `end\` {
		t.Fatalf("want literal backslash, got %q err=%v", got, err)
	}
}

func TestRedactedURL(t *testing.T) {
	if got := redactedURL("http://user:pass@config:8888"); got != "http://user:xxxxx@config:8888" {
		t.Fatalf("unexpected redaction %q", got)
	}
	if got := redactedURL("%zz://bad"); got != "" {
		t.Fatalf("want empty for unparsable url, got %q", got)
	}
}
