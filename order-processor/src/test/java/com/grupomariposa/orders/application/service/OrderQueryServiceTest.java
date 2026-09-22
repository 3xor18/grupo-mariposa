package com.grupomariposa.orders.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.grupomariposa.orders.application.port.out.OrderQueryRepository;
import com.grupomariposa.orders.application.query.OrderSearchCriteria;
import com.grupomariposa.orders.application.query.OrderSummary;
import com.grupomariposa.orders.application.query.PageResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderQueryServiceTest {

    private final OrderQueryRepository repository = mock(OrderQueryRepository.class);
    private final OrderQueryService service = new OrderQueryService(repository);

    @Test
    void should_delegate_lookups_to_repository() {
        final OrderSearchCriteria criteria = new OrderSearchCriteria(null, null, 0, 20);
        final PageResult<OrderSummary> page = new PageResult<>(List.of(), 0, 20, 0);
        when(repository.findById("ORD-1")).thenReturn(Optional.empty());
        when(repository.search(criteria)).thenReturn(page);

        assertThat(service.find("ORD-1")).isEmpty();
        assertThat(service.list(criteria)).isSameAs(page);
    }
}
