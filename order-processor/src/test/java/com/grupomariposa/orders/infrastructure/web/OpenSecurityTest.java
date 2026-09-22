package com.grupomariposa.orders.infrastructure.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.grupomariposa.orders.application.port.in.FindOrderQuery;
import com.grupomariposa.orders.application.port.in.ListOrdersQuery;
import com.grupomariposa.orders.application.query.PageResult;
import com.grupomariposa.orders.infrastructure.config.WebConfiguration;
import com.grupomariposa.orders.infrastructure.observability.TraceContext;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrdersController.class)
@Import({WebConfiguration.class, OrdersControllerTest.Support.class})
@TestPropertySource(properties = {
    "app.security.enabled=false",
    "app.security.allowed-origins=*",
    "app.security.reader-role=orders-reader",
    "app.security.admin-role=orders-admin",
    "app.api.orders.default-page-size=20",
    "app.api.orders.max-page-size=100",
    "app.api.orders.max-offset=10000"
})
class OpenSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FindOrderQuery findOrder;

    @MockitoBean
    private ListOrdersQuery listOrders;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private TraceContext traceContext;

    @Test
    void should_allow_anonymous_reads_when_auth_is_disabled() throws Exception {
        when(traceContext.currentTraceId()).thenReturn(Optional.empty());
        when(listOrders.list(any())).thenReturn(new PageResult<>(List.of(), 0, 20, 0));

        mockMvc.perform(get("/orders")).andExpect(status().isOk());
        mockMvc.perform(get("/orders/ORD-NONE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }
}
