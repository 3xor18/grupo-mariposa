package com.grupomariposa.orders.infrastructure.observability;

import java.util.regex.Pattern;

public final class CauseSanitizer {

    public static final int MAX_CAUSE_LENGTH = 256;
    public static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final String UNKNOWN = "unknown";
    private static final String MASK = "[redacted]";
    private static final String SPACE = " ";
    private static final String EMPTY = "";
    private static final String DESCRIPTION_SEPARATOR = ": ";
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._~+/=-]+");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|secret|token|api[_-]?key)\\s*[=:]\\s*\\S+");
    private static final Pattern UNSAFE_ID = Pattern.compile("[^A-Za-z0-9._:-]");

    public String cause(final String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String cleaned = CONTROL.matcher(raw).replaceAll(SPACE);
        cleaned = BEARER.matcher(cleaned).replaceAll(MASK);
        cleaned = SECRET.matcher(cleaned).replaceAll(MASK);
        cleaned = EMAIL.matcher(cleaned).replaceAll(MASK);
        cleaned = WHITESPACE.matcher(cleaned).replaceAll(SPACE).trim();
        return truncate(cleaned, MAX_CAUSE_LENGTH);
    }

    public String describe(final Throwable failure) {
        return failure.getClass().getSimpleName() + DESCRIPTION_SEPARATOR
                + cause(failure.getMessage());
    }

    public String identifier(final String raw) {
        if (raw == null) {
            return EMPTY;
        }
        return truncate(UNSAFE_ID.matcher(raw).replaceAll(EMPTY), MAX_IDENTIFIER_LENGTH);
    }

    private static String truncate(final String value, final int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
