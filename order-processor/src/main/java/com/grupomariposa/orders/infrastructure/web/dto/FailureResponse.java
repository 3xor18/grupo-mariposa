package com.grupomariposa.orders.infrastructure.web.dto;

public record FailureResponse(String category, String cause, int attempts) {
}
