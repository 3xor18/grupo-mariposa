package com.grupomariposa.orders.infrastructure.web;

import java.util.Locale;

public enum ApiErrorCode {
    VALIDATION_ERROR,
    ORDER_NOT_FOUND,
    NOT_FOUND,
    SERVICE_UNAVAILABLE,
    METHOD_NOT_ALLOWED,
    UNAUTHORIZED,
    FORBIDDEN,
    TAX_RATE_NOT_FOUND,
    TAX_RATE_CONFLICT,
    FOUR_EYES_REQUIRED,
    INTERNAL_ERROR;

    private static final char UNDERSCORE = '_';
    private static final char HYPHEN = '-';

    public String slug() {
        return name().toLowerCase(Locale.ROOT).replace(UNDERSCORE, HYPHEN);
    }
}
