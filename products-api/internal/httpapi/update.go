package httpapi

import (
	"bytes"
	"encoding/json"
	"errors"
	"maps"
	"net/http"
	"slices"
	"strconv"
	"strings"

	"github.com/grupomariposa/platform/products-api/internal/catalog"
)

const (
	maxUpdateBodyBytes = 16 << 10
	ifMatchAny         = "*"
	jsonNull           = "null"
	fieldIfMatch       = "If-Match"
	messageNotObject   = "must be a JSON object of at most 16 KiB"
	messageNotString   = "must be a string"
	messageUnknown     = "is not a supported field"
	messageBadIfMatch  = `must be a version such as "3"`
	versionBits        = 64
	minVersion         = 1
)

var errNotSingleObject = errors.New("body must be a single JSON object")

func decodeUpdate(w http.ResponseWriter, r *http.Request) (catalog.UpdateCommand, []fieldError) {
	cmd := catalog.UpdateCommand{
		ProductID: r.PathValue(pathProductID),
		Market:    r.URL.Query().Get(queryMarket),
	}
	var invalid []fieldError
	version, ok := parseIfMatch(r.Header.Get(headerIfMatch))
	if !ok {
		invalid = append(invalid, fieldError{Field: fieldIfMatch, Message: messageBadIfMatch})
	}
	cmd.ExpectedVersion = version
	fields, err := readObject(w, r)
	if err != nil {
		return cmd, append(invalid, fieldError{Field: catalog.FieldBody, Message: messageNotObject})
	}
	for _, name := range slices.Sorted(maps.Keys(fields)) {
		if problem := assign(&cmd, name, fields[name]); problem != nil {
			invalid = append(invalid, *problem)
		}
	}
	return cmd, invalid
}

func readObject(w http.ResponseWriter, r *http.Request) (map[string]json.RawMessage, error) {
	var fields map[string]json.RawMessage
	decoder := json.NewDecoder(http.MaxBytesReader(w, r.Body, maxUpdateBodyBytes))
	if err := decoder.Decode(&fields); err != nil {
		return nil, err
	}
	if fields == nil || decoder.More() {
		return nil, errNotSingleObject
	}
	return fields, nil
}

func assign(cmd *catalog.UpdateCommand, name string, raw json.RawMessage) *fieldError {
	var target **string
	switch name {
	case catalog.FieldName:
		target = &cmd.Name
	case catalog.FieldStatus:
		target = &cmd.Status
	case catalog.FieldTaxCategory:
		target = &cmd.TaxCategory
	default:
		return &fieldError{Field: name, Message: messageUnknown}
	}
	var value string
	if bytes.Equal(bytes.TrimSpace(raw), []byte(jsonNull)) || json.Unmarshal(raw, &value) != nil {
		return &fieldError{Field: name, Message: messageNotString}
	}
	*target = &value
	return nil
}

func parseIfMatch(header string) (*int64, bool) {
	header = strings.TrimSpace(header)
	if header == "" || header == ifMatchAny {
		return nil, true
	}
	unquoted := strings.TrimSuffix(strings.TrimPrefix(header, etagQuote), etagQuote)
	version, err := strconv.ParseInt(unquoted, versionBase, versionBits)
	if err != nil || version < minVersion {
		return nil, false
	}
	return &version, true
}
