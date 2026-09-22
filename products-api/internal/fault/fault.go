package fault

import (
	"errors"
	"fmt"
	"strconv"
	"strings"
	"sync/atomic"
)

type Kind string

const (
	KindBadRequest         Kind = "400"
	KindTooManyRequests    Kind = "429"
	KindInternalError      Kind = "500"
	KindBadGateway         Kind = "502"
	KindServiceUnavailable Kind = "503"
	KindTimeout            Kind = "timeout"
)

const (
	ruleSeparator  = ","
	fieldSeparator = ":"
	minFields      = 2
	maxFields      = 3
	alwaysFail     = 0
)

var ErrInvalidRule = errors.New("invalid fault rule")

var kinds = map[Kind]struct{}{
	KindBadRequest: {}, KindTooManyRequests: {}, KindInternalError: {},
	KindBadGateway: {}, KindServiceUnavailable: {}, KindTimeout: {},
}

type Rule struct {
	ID    string
	Kind  Kind
	Times int64
}

func ParseRules(raw string) ([]Rule, error) {
	var rules []Rule
	seen := map[string]struct{}{}
	for _, entry := range strings.Split(raw, ruleSeparator) {
		entry = strings.TrimSpace(entry)
		if entry == "" {
			continue
		}
		rule, err := parseRule(entry)
		if err != nil {
			return nil, err
		}
		if _, dup := seen[rule.ID]; dup {
			return nil, fmt.Errorf("%w: duplicated id %q", ErrInvalidRule, rule.ID)
		}
		seen[rule.ID] = struct{}{}
		rules = append(rules, rule)
	}
	return rules, nil
}

func parseRule(entry string) (Rule, error) {
	fields := strings.Split(entry, fieldSeparator)
	if len(fields) < minFields || len(fields) > maxFields || fields[0] == "" {
		return Rule{}, fmt.Errorf("%w: %q", ErrInvalidRule, entry)
	}
	kind := Kind(fields[1])
	if _, ok := kinds[kind]; !ok {
		return Rule{}, fmt.Errorf("%w: unknown type in %q", ErrInvalidRule, entry)
	}
	rule := Rule{ID: fields[0], Kind: kind, Times: alwaysFail}
	if len(fields) == maxFields {
		times, err := strconv.ParseInt(fields[2], 10, 64)
		if err != nil || times <= alwaysFail {
			return Rule{}, fmt.Errorf("%w: invalid times in %q", ErrInvalidRule, entry)
		}
		rule.Times = times
	}
	return rule, nil
}

type Injector struct {
	rules map[string]*activeRule
}

type activeRule struct {
	rule Rule
	hits atomic.Int64
}

func NewInjector(rules []Rule) *Injector {
	active := make(map[string]*activeRule, len(rules))
	for _, r := range rules {
		active[r.ID] = &activeRule{rule: r}
	}
	return &Injector{rules: active}
}

func (i *Injector) Next(id string) (Kind, bool) {
	active, ok := i.rules[id]
	if !ok {
		return "", false
	}
	if active.rule.Times == alwaysFail {
		return active.rule.Kind, true
	}
	if active.hits.Add(1) > active.rule.Times {
		return "", false
	}
	return active.rule.Kind, true
}
