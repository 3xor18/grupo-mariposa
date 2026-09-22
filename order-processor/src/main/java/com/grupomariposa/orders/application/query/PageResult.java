package com.grupomariposa.orders.application.query;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    private static final String INVALID_SIZE = "Page size must be positive";

    public PageResult {
        items = List.copyOf(items);
        if (size < 1) {
            throw new IllegalArgumentException(INVALID_SIZE);
        }
    }

    public int totalPages() {
        return Math.toIntExact((totalElements + size - 1) / size);
    }
}
