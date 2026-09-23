package com.grupomariposa.orders.infrastructure.kafka.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.grupomariposa.orders.application.validation.UnvalidatedItem;
import com.grupomariposa.orders.application.validation.UnvalidatedOrder;
import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class OrderMessageMapperTest {

    private final OrderMessageMapper mapper = new OrderMessageMapper();

    @Test
    void should_map_every_field_and_keep_null_items() {
        final OrderCreatedMessage message = new OrderCreatedMessage("E1", 2,
                "2026-09-18T15:42:10Z", "O1", "MX", "MXN", "C1", "web",
                Arrays.asList(new OrderItemMessage("P1", 3L, BigDecimal.TEN), null));

        assertThat(mapper.toUnvalidated(message)).isEqualTo(new UnvalidatedOrder("E1", 2,
                "2026-09-18T15:42:10Z", "O1", "MX", "MXN", "C1", "web",
                Arrays.asList(new UnvalidatedItem("P1", 3L, BigDecimal.TEN), null)));
    }

    @Test
    void should_keep_missing_items_missing() {
        assertThat(mapper.toUnvalidated(new OrderCreatedMessage(null, null, null, null, null,
                null, null, null, null)).items()).isNull();
    }
}
