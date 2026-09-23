package com.grupomariposa.orders.infrastructure.persistence.document;

import java.time.Instant;
import org.bson.types.Decimal128;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = TaxRateDocument.COLLECTION)
public record TaxRateDocument(
        @Id String id,
        String market,
        String category,
        Decimal128 rate,
        Instant validFrom,
        Instant validTo,
        String status,
        String proposedBy,
        Instant proposedAt,
        String approvedBy,
        Instant approvedAt,
        String rejectedBy,
        Instant rejectedAt,
        String changeReason,
        long version) {

    public static final String COLLECTION = "tax_rates";
    public static final String GUARDS = "tax_rate_guards";
    public static final String CATEGORY = "category";
    public static final String VALID_FROM = "validFrom";
    public static final String VERSION = "version";
}
