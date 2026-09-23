package httpapi

import (
	"errors"
	"net/http"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/product"
)

const (
	ifMatchAny       = "*"
	weakPrefix       = "W/"
	quote            = '"'
	listSeparator    = ','
	headerJoin       = ", "
	space            = ' '
	tab              = '\t'
	exclamation      = 0x21
	printableFrom    = 0x23
	printableTo      = 0x7E
	obsoleteTextFrom = 0x80
)

var errMalformedIfMatch = errors.New("malformed If-Match header")

type ifMatch struct {
	precondition *product.Precondition
}

func parseIfMatch(header http.Header) (ifMatch, error) {
	values, present := header[http.CanonicalHeaderKey(headerIfMatch)]
	raw := strings.TrimSpace(strings.Join(values, headerJoin))
	if !present || raw == ifMatchAny {
		return ifMatch{}, nil
	}
	precondition := &product.Precondition{StrongTags: []string{}}
	for rest := raw; ; {
		tag, weak, next, err := nextEntityTag(rest)
		if err != nil {
			return ifMatch{}, err
		}
		if !weak {
			precondition.StrongTags = append(precondition.StrongTags, tag)
		}
		if next == "" {
			return ifMatch{precondition: precondition}, nil
		}
		rest = next
	}
}

func nextEntityTag(input string) (string, bool, string, error) {
	item := strings.TrimLeft(input, " \t")
	weak := strings.HasPrefix(item, weakPrefix)
	if weak {
		item = item[len(weakPrefix):]
	}
	tag, rest, ok := opaqueTag(item, weak)
	if !ok {
		return "", false, "", errMalformedIfMatch
	}
	rest = strings.TrimLeft(rest, " \t")
	if rest == "" {
		return tag, weak, "", nil
	}
	if rest[0] != listSeparator || strings.TrimSpace(rest[1:]) == "" {
		return "", false, "", errMalformedIfMatch
	}
	return tag, weak, rest[1:], nil
}

func opaqueTag(item string, quotedOnly bool) (string, string, bool) {
	if item != "" && item[0] == quote {
		end := strings.IndexByte(item[1:], quote)
		if end < 0 || !validTagChars(item[1:end+1]) {
			return "", "", false
		}
		return item[1 : end+1], item[end+2:], true
	}
	end := strings.IndexFunc(item, func(r rune) bool {
		return r == listSeparator || r == space || r == tab
	})
	if end < 0 {
		end = len(item)
	}
	bare := item[:end]
	return bare, item[end:], !quotedOnly && bare != "" && validTagChars(bare)
}

func validTagChars(tag string) bool {
	for i := 0; i < len(tag); i++ {
		c := tag[i]
		if c != exclamation && (c < printableFrom || c > printableTo) && c < obsoleteTextFrom {
			return false
		}
	}
	return true
}
