package com.grupomariposa.orders.domain.model;

import java.time.Instant;
import java.util.Objects;

public record TaxRateReview(String reviewer, Instant reviewedAt) {

    public TaxRateReview {
        Objects.requireNonNull(reviewer, "reviewer");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
    }
}
