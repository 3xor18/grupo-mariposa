package com.grupomariposa.orders.application.validation;

import java.math.BigDecimal;

public record UnvalidatedItem(String productId, Long quantity, BigDecimal unitPrice) {
}
