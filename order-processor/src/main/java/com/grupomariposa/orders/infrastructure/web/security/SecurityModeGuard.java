package com.grupomariposa.orders.infrastructure.web.security;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

public final class SecurityModeGuard {

    public static final String LOCAL_PROFILE = "local";
    private static final String DISABLED_OUTSIDE_LOCAL =
            "Authentication can only be disabled with the '" + LOCAL_PROFILE + "' profile";

    private SecurityModeGuard() {
    }

    public static void requireLocalWhenDisabled(final boolean enabled,
                                                final Environment environment) {
        if (!enabled && !environment.acceptsProfiles(Profiles.of(LOCAL_PROFILE))) {
            throw new IllegalStateException(DISABLED_OUTSIDE_LOCAL);
        }
    }
}
