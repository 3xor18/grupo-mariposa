import { ReadinessState } from './readiness.state';

describe('ReadinessState', () => {
  it('should_be_ready_only_between_bootstrap_and_draining', () => {
    const state = new ReadinessState();
    expect(state.isReady()).toBe(false);

    state.onApplicationBootstrap();
    expect(state.isReady()).toBe(true);

    state.markDraining();
    expect(state.isReady()).toBe(false);
  });
});
