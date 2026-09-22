package fault_test

import (
	"errors"
	"sync"
	"sync/atomic"
	"testing"

	"github.com/grupomariposa/platform/products-api/internal/fault"
)

func TestParseRules(t *testing.T) {
	rules, err := fault.ParseRules(" PRD-012:503:2, PRD-013:timeout ,PRD-014:400,,")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	want := []fault.Rule{
		{ID: "PRD-012", Kind: fault.KindServiceUnavailable, Times: 2},
		{ID: "PRD-013", Kind: fault.KindTimeout},
		{ID: "PRD-014", Kind: fault.KindBadRequest},
	}
	if len(rules) != len(want) {
		t.Fatalf("want %d rules, got %d", len(want), len(rules))
	}
	for i := range want {
		if rules[i] != want[i] {
			t.Fatalf("rule %d: want %+v, got %+v", i, want[i], rules[i])
		}
	}
}

func TestParseRulesEmpty(t *testing.T) {
	rules, err := fault.ParseRules("")
	if err != nil || len(rules) != 0 {
		t.Fatalf("want no rules, got %v err=%v", rules, err)
	}
}

func TestParseRulesRejectsInvalid(t *testing.T) {
	cases := map[string]string{
		"should_reject_missing_type":   "PRD-1",
		"should_reject_empty_id":       ":500",
		"should_reject_too_many":       "PRD-1:500:1:2",
		"should_reject_unknown_type":   "PRD-1:418",
		"should_reject_zero_times":     "PRD-1:500:0",
		"should_reject_non_numeric":    "PRD-1:500:x",
		"should_reject_duplicated_ids": "PRD-1:500,PRD-1:503",
	}
	for name, raw := range cases {
		t.Run(name, func(t *testing.T) {
			if _, err := fault.ParseRules(raw); !errors.Is(err, fault.ErrInvalidRule) {
				t.Fatalf("want ErrInvalidRule, got %v", err)
			}
		})
	}
}

func TestInjectorFailsFirstNTimes(t *testing.T) {
	injector := fault.NewInjector([]fault.Rule{
		{ID: "PRD-012", Kind: fault.KindServiceUnavailable, Times: 2},
		{ID: "PRD-014", Kind: fault.KindBadRequest},
	})
	sequence := []bool{true, true, false, false}
	for i, want := range sequence {
		kind, got := injector.Next("PRD-012")
		if got != want || (want && kind != fault.KindServiceUnavailable) {
			t.Fatalf("call %d: want %v, got %v (%s)", i, want, got, kind)
		}
	}
	for range 3 {
		if kind, ok := injector.Next("PRD-014"); !ok || kind != fault.KindBadRequest {
			t.Fatal("always rule must always fail")
		}
	}
	if _, ok := injector.Next("PRD-001"); ok {
		t.Fatal("unknown id must not fail")
	}
}

func TestInjectorIsConcurrencySafe(t *testing.T) {
	const times, callers = 5, 64
	injector := fault.NewInjector([]fault.Rule{{ID: "X", Kind: fault.KindTimeout, Times: times}})
	var failures atomic.Int64
	var wg sync.WaitGroup
	for range callers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			if _, ok := injector.Next("X"); ok {
				failures.Add(1)
			}
		}()
	}
	wg.Wait()
	if failures.Load() != times {
		t.Fatalf("want %d failures, got %d", times, failures.Load())
	}
}
