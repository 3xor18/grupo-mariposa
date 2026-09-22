package com.grupomariposa.orders.infrastructure.web;

import com.grupomariposa.orders.application.port.in.FindOrderQuery;
import com.grupomariposa.orders.application.port.in.ListOrdersQuery;
import com.grupomariposa.orders.infrastructure.web.dto.OrderPageResponse;
import com.grupomariposa.orders.infrastructure.web.dto.OrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.Objects;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = OrdersController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = OrdersController.SECURITY_SCHEME)
public class OrdersController {

    public static final String BASE_PATH = "/orders";
    public static final String SECURITY_SCHEME = "bearerAuth";

    private final FindOrderQuery findOrder;
    private final ListOrdersQuery listOrders;
    private final OrderRequestParser parser;
    private final OrderResponseMapper mapper;

    public OrdersController(final FindOrderQuery findOrder, final ListOrdersQuery listOrders,
                            final OrderRequestParser parser, final OrderResponseMapper mapper) {
        this.findOrder = Objects.requireNonNull(findOrder, "findOrder");
        this.listOrders = Objects.requireNonNull(listOrders, "listOrders");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Operation(operationId = "getOrder", summary = "Get the processing result of an order")
    @GetMapping("/{orderId}")
    public OrderResponse getOrder(@PathVariable("orderId") final String orderId) {
        final String validId = parser.orderId(orderId);
        return findOrder.find(validId)
                .map(mapper::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(validId));
    }

    @Operation(operationId = "listOrders", summary = "List processed orders, newest first")
    @GetMapping
    public OrderPageResponse listOrders(
            @RequestParam(name = "status", required = false) final String status,
            @RequestParam(name = "market", required = false) final String market,
            @RequestParam(name = "page", required = false) final String page,
            @RequestParam(name = "size", required = false) final String size) {
        return mapper.toPage(listOrders.list(parser.criteria(status, market, page, size)));
    }
}
