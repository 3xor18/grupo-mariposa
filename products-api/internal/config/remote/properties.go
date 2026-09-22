package remote

import (
	"bufio"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"strings"
	"unicode"
	"unicode/utf16"
)

const (
	escapeChar       = '\\'
	unicodeEscape    = 'u'
	unicodeDigits    = 4
	bitsPerByte      = 8
	commentHash      = "#"
	commentBang      = "!"
	envWordSeparator = "_"
	oddCount         = 1
	pairLength       = 2
)

var errBadUnicodeEscape = errors.New("malformed unicode escape")

func parseProperties(r io.Reader) (map[string]string, error) {
	scanner := bufio.NewScanner(r)
	lines := logicalLines(scanner)
	if err := scanner.Err(); err != nil {
		return nil, fmt.Errorf("read properties: %w", err)
	}
	properties := make(map[string]string, len(lines))
	for _, line := range lines {
		key, value, err := splitEntry(line)
		if err != nil {
			return nil, err
		}
		properties[key] = value
	}
	return properties, nil
}

func envKey(property string) string {
	replacer := strings.NewReplacer(".", envWordSeparator, "-", envWordSeparator)
	return strings.ToUpper(replacer.Replace(strings.TrimFunc(property, isBlank)))
}

func isBlank(r rune) bool {
	return r == ' ' || r == '\t' || r == '\f'
}

func isSeparator(r rune) bool {
	return r == '=' || r == ':'
}

func logicalLines(scanner *bufio.Scanner) []string {
	var lines []string
	var pending strings.Builder
	for scanner.Scan() {
		line := strings.TrimLeftFunc(scanner.Text(), isBlank)
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
	return trailingEscapes([]rune(line), len([]rune(line)))%pairLength == oddCount
}

func trailingEscapes(runes []rune, end int) int {
	count := 0
	for i := end - 1; i >= 0 && runes[i] == escapeChar; i-- {
		count++
	}
	return count
}

func splitEntry(line string) (string, string, error) {
	runes := []rune(line)
	end := separatorIndex(runes)
	key, err := unescape(runes[:end])
	if err != nil {
		return "", "", fmt.Errorf("property key %q: %w", string(runes[:end]), err)
	}
	value, err := unescape(trimTrailingBlanks(skipSeparator(runes[end:])))
	if err != nil {
		return "", "", fmt.Errorf("property %q value: %w", key, err)
	}
	return key, value, nil
}

func separatorIndex(runes []rune) int {
	for i := 0; i < len(runes); i++ {
		switch {
		case runes[i] == escapeChar:
			i++
		case isSeparator(runes[i]) || isBlank(runes[i]):
			return i
		}
	}
	return len(runes)
}

func skipSeparator(rest []rune) []rune {
	rest = trimLeadingBlanks(rest)
	if len(rest) > 0 && isSeparator(rest[0]) {
		rest = trimLeadingBlanks(rest[1:])
	}
	return rest
}

func trimLeadingBlanks(runes []rune) []rune {
	for len(runes) > 0 && isBlank(runes[0]) {
		runes = runes[1:]
	}
	return runes
}

func trimTrailingBlanks(runes []rune) []rune {
	end := len(runes)
	for end > 0 && isBlank(runes[end-1]) && trailingEscapes(runes, end-1)%pairLength == 0 {
		end--
	}
	return runes[:end]
}

func unescape(runes []rune) (string, error) {
	out := make([]rune, 0, len(runes))
	for i := 0; i < len(runes); i++ {
		if runes[i] != escapeChar || i+1 == len(runes) {
			out = append(out, runes[i])
			continue
		}
		i++
		decoded, consumed, err := decodeEscape(runes[i:])
		if err != nil {
			return "", err
		}
		out = append(out, decoded)
		i += consumed
	}
	return string(combineSurrogates(out)), nil
}

func decodeEscape(runes []rune) (rune, int, error) {
	switch runes[0] {
	case 't':
		return '\t', 0, nil
	case 'n':
		return '\n', 0, nil
	case 'r':
		return '\r', 0, nil
	case 'f':
		return '\f', 0, nil
	case unicodeEscape:
		return decodeUnicode(runes[1:])
	default:
		return runes[0], 0, nil
	}
}

func decodeUnicode(digits []rune) (rune, int, error) {
	if len(digits) < unicodeDigits {
		return 0, 0, errBadUnicodeEscape
	}
	decoded, err := hex.DecodeString(string(digits[:unicodeDigits]))
	if err != nil {
		return 0, 0, fmt.Errorf("%w: %w", errBadUnicodeEscape, err)
	}
	return rune(decoded[0])<<bitsPerByte | rune(decoded[1]), unicodeDigits, nil
}

func combineSurrogates(runes []rune) []rune {
	out := make([]rune, 0, len(runes))
	for i := 0; i < len(runes); i++ {
		if i+1 < len(runes) && utf16.IsSurrogate(runes[i]) {
			if pair := utf16.DecodeRune(runes[i], runes[i+1]); pair != unicode.ReplacementChar {
				out = append(out, pair)
				i++
				continue
			}
		}
		out = append(out, runes[i])
	}
	return out
}
