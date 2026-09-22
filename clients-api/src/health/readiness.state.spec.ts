import { ReadinessState } from './readiness.state';

describe('ReadinessState', () => {
  it('should_be_ready_only_between_bootstrap_and_shutdown', () => {
    const state = new ReadinessState();
    expect(state.isReady()).toBe(false);

    state.onApplicationBootstrap();
    expect(state.isReady()).toBe(true);

    state.beforeApplicationShutdown();
    expect(state.isReady()).toBe(false);
  });
});
