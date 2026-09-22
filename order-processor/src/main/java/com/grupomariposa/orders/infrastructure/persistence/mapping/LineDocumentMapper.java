package com.grupomariposa.orders.infrastructure.persistence.mapping;

import com.grupomariposa.orders.domain.model.LineAmounts;
import com.grupomariposa.orders.domain.model.OrderLine;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.infrastructure.persistence.document.LineDocument;

final class LineDocumentMapper {

    LineDocument toDocument(final OrderLine line) {
        final LineAmounts amounts = line.amounts();
        final boolean priced = amounts != null;
        return new LineDocument(line.productId(), line.name(), line.sku(),
                OrderDocumentMapper.nameOf(line.taxCategory()), line.quantity(),
                Decimals.of(line.unitPrice()),
                priced ? Decimals.of(amounts.grossSubtotal()) : null,
                priced ? Decimals.of(amounts.discountRate()) : null,
                priced ? Decimals.of(amounts.discount()) : null,
                priced ? Decimals.of(amounts.netSubtotal()) : null,
                priced ? Decimals.of(amounts.taxRate()) : null,
                priced ? Decimals.of(amounts.taxAmount()) : null,
                priced ? Decimals.of(amounts.lineTotal()) : null);
    }

    OrderLine toDomain(final LineDocument document) {
        return new OrderLine(document.productId(), document.name(), document.sku(),
                OrderDocumentMapper.nullable(document.taxCategory(), TaxCategory::valueOf),
                document.quantity(), Decimals.toBigDecimal(document.unitPrice()),
                document.grossSubtotal() == null ? null : amounts(document));
    }

    private static LineAmounts amounts(final LineDocument document) {
        return new LineAmounts(Decimals.toMoney(document.grossSubtotal()),
                Decimals.toRate(document.discountRate()), Decimals.toMoney(document.discount()),
                Decimals.toMoney(document.netSubtotal()), Decimals.toRate(document.taxRate()),
                Decimals.toMoney(document.taxAmount()), Decimals.toMoney(document.lineTotal()));
    }
}
