package com.grupomariposa.orders.application.command;

import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.TaxCategory;
import java.math.BigDecimal;
import java.time.Instant;

public record TaxRateProposalCommand(MarketCode market, TaxCategory category, BigDecimal rate,
                                     Instant validFrom, Instant validTo, String changeReason,
                                     String proposer) {
}
