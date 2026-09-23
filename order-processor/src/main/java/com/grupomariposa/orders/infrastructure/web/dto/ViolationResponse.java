package com.grupomariposa.orders.infrastructure.web.dto;

public record ViolationResponse(String code, String message, String productId) {
}
