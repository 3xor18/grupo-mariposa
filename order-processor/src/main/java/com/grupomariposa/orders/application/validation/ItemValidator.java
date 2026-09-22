package com.grupomariposa.orders.application.validation;

import com.grupomariposa.orders.domain.model.RequestedItem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ItemValidator {

    private static final String ITEMS = "items";
    private static final String ITEM_FIELD = "items[%d].%s";
    private static final String ITEM = "items[%d]";
    private static final String PRODUCT_ID = "productId";
    private static final String QUANTITY = "quantity";
    private static final String UNIT_PRICE = "unitPrice";
    private static final String EMPTY_ITEMS = "must contain at least one item";
    private static final String TOO_MANY_ITEMS = "must contain at most %d items";
    private static final String POSITIVE_QUANTITY = "must be an integer greater than 0";
    private static final String DUPLICATED_PRODUCT = "is duplicated within the order";

    private final ContractRules rules;
    private final PriceRule priceRule;

    ItemValidator(final ContractRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
        this.priceRule = new PriceRule(rules);
    }

    List<RequestedItem> validate(final List<UnvalidatedItem> items, final ErrorCollector errors) {
        if (!errors.requirePresent(ITEMS, items)) {
            return List.of();
        }
        if (items.isEmpty()) {
            errors.add(ITEMS, EMPTY_ITEMS);
        }
        if (items.size() > OrderCommandValidator.MAX_ITEMS) {
            errors.add(ITEMS, TOO_MANY_ITEMS.formatted(OrderCommandValidator.MAX_ITEMS));
        }
        final Set<String> seen = new HashSet<>();
        final List<RequestedItem> valid = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            final ErrorCollector itemErrors = new ErrorCollector();
            validateItem(index, items.get(index), seen, itemErrors);
            if (itemErrors.isEmpty()) {
                final UnvalidatedItem item = items.get(index);
                valid.add(new RequestedItem(item.productId(), Math.toIntExact(item.quantity()),
                        item.unitPrice()));
            }
            errors.addAll(itemErrors);
        }
        return List.copyOf(valid);
    }

    private void validateItem(final int index, final UnvalidatedItem item, final Set<String> seen,
                              final ErrorCollector errors) {
        if (!errors.requirePresent(ITEM.formatted(index), item)) {
            return;
        }
        final String productField = ITEM_FIELD.formatted(index, PRODUCT_ID);
        errors.requireIdentifier(productField, item.productId(),
                OrderCommandValidator.MAX_ID_LENGTH, rules.productIdPattern());
        if (item.productId() != null && !seen.add(item.productId())) {
            errors.add(productField, DUPLICATED_PRODUCT);
        }
        final String quantityField = ITEM_FIELD.formatted(index, QUANTITY);
        if (errors.requirePresent(quantityField, item.quantity())
                && (item.quantity() <= 0 || item.quantity() > Integer.MAX_VALUE)) {
            errors.add(quantityField, POSITIVE_QUANTITY);
        }
        priceRule.check(ITEM_FIELD.formatted(index, UNIT_PRICE), item.unitPrice(), errors);
    }
}
