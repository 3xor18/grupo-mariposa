package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderItemMessage(String productId, Long quantity, BigDecimal unitPrice) {
}
