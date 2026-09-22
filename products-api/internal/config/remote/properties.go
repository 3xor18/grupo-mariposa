package remote

import (
	"bufio"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"strings"
	"unicode"
)

const (
	escapeChar       = '\\'
	unicodeEscape    = 'u'
	unicodeDigits    = 4
	bitsPerByte      = 8
	commentHash      = "#"
	commentBang      = "!"
	envWordSeparator = "_"
)

var (
	errBadUnicodeEscape = errors.New("malformed unicode escape")
	keySeparators       = map[rune]bool{'=': true, ':': true}
	simpleEscapes       = map[rune]rune{'t': '\t', 'n': '\n', 'r': '\r', 'f': '\f'}
	envKeyReplacer      = strings.NewReplacer(".", envWordSeparator, "-", envWordSeparator)
)

func ParseProperties(r io.Reader) (map[string]string, error) {
	properties := map[string]string{}
	scanner := bufio.NewScanner(r)
	lines := logicalLines(scanner)
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("read properties: %w", err)
	}
	for _, line := range lines {
		key, value, err := splitEntry(line)
		if err != nil {
			return nil, err
		}
		properties[key] = value
	}
	return properties, nil
}

func EnvKey(property string) string {
	return strings.ToUpper(envKeyReplacer.Replace(strings.TrimSpace(property)))
}

func logicalLines(scanner *bufio.Scanner) []string {
	var lines []string
	var pending strings.Builder
	for scanner.Scan() {
		line := strings.TrimLeftFunc(scanner.Text(), unicode.IsSpace)
		if pending.Len() == 0 && isSkippable(line) {
			continue
		}
		if continues(line) {
			pending.WriteString(line[:len(line)-1])
			continue
		}
		pending.WriteString(line)
		lines = append(lines, pending.String())
		pending.Reset()
	}
	if pending.Len() > 0 {
		lines = append(lines, pending.String())
	}
	return lines
}

func isSkippable(line string) bool {
	return line == "" || strings.HasPrefix(line, commentHash) || strings.HasPrefix(line, commentBang)
}

func continues(line string) bool {
	trailing := len(line) - len(strings.TrimRight(line, string(escapeChar)))
	return trailing%2 == 1
}

func splitEntry(line string) (string, string, error) {
	runes := []rune(line)
	end := separatorIndex(runes)
	key, err := unescape(runes[:end])
	if err != nil {
		return "", "", fmt.Errorf("property key %q: %w", string(runes[:end]), err)
	}
	value, err := unescape(skipSeparator(runes[end:]))
	if err != nil {
		return "", "", fmt.Errorf("property %q value: %w", key, err)
	}
	return key, strings.TrimSpace(value), nil
}

func separatorIndex(runes []rune) int {
	for i := 0; i < len(runes); i++ {
		switch {
		case runes[i] == escapeChar:
			i++
		case keySeparators[runes[i]] || unicode.IsSpace(runes[i]):
			return i
		}
	}
	return len(runes)
}

func skipSeparator(rest []rune) []rune {
	rest = trimLeadingSpace(rest)
	if len(rest) > 0 && keySeparators[rest[0]] {
		rest = trimLeadingSpace(rest[1:])
	}
	return rest
}

func trimLeadingSpace(runes []rune) []rune {
	for len(runes) > 0 && unicode.IsSpace(runes[0]) {
		runes = runes[1:]
	}
	return runes
}

func unescape(runes []rune) (string, error) {
	var out strings.Builder
	for i := 0; i < len(runes); i++ {
		if runes[i] != escapeChar || i+1 == len(runes) {
			out.WriteRune(runes[i])
			continue
		}
		i++
		decoded, consumed, err := decodeEscape(runes[i:])
		if err != nil {
			return "", err
		}
		out.WriteRune(decoded)
		i += consumed
	}
	return out.String(), nil
}

func decodeEscape(runes []rune) (rune, int, error) {
	if mapped, ok := simpleEscapes[runes[0]]; ok {
		return mapped, 0, nil
	}
	if runes[0] != unicodeEscape {
		return runes[0], 0, nil
	}
	if len(runes) <= unicodeDigits {
		return 0, 0, errBadUnicodeEscape
	}
	decoded, err := hex.DecodeString(string(runes[1 : 1+unicodeDigits]))
	if err != nil {
		return 0, 0, fmt.Errorf("%w: %w", errBadUnicodeEscape, err)
	}
	return rune(decoded[0])<<bitsPerByte | rune(decoded[1]), unicodeDigits, nil
}
