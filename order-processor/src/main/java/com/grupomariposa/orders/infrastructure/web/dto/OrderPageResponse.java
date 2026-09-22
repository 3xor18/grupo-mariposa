package com.grupomariposa.orders.infrastructure.web.dto;

import java.util.List;

public record OrderPageResponse(
        List<OrderSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
