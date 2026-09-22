package com.grupomariposa.orders.application.query;

import java.util.List;

public record PageResult<T>(List<T> items, int page, int size, long totalElements) {

    public PageResult {
        items = List.copyOf(items);
    }

    public int totalPages() {
        return Math.toIntExact((totalElements + size - 1) / size);
    }
}
