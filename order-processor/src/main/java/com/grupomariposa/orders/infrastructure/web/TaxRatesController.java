package com.grupomariposa.orders.infrastructure.web;

import com.grupomariposa.orders.application.port.in.TaxRateAdministration;
import com.grupomariposa.orders.infrastructure.web.dto.TaxRateRequest;
import com.grupomariposa.orders.infrastructure.web.dto.TaxRateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = TaxRatesController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@SecurityRequirement(name = OrdersController.SECURITY_SCHEME)
public class TaxRatesController {

    public static final String BASE_PATH = "/tax-rates";

    private final TaxRateAdministration administration;
    private final TaxRateWebMapper mapper;

    public TaxRatesController(final TaxRateAdministration administration,
                              final TaxRateWebMapper mapper) {
        this.administration = Objects.requireNonNull(administration, "administration");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Operation(operationId = "listTaxRates", summary = "List tax rate periods and proposals")
    @GetMapping
    public List<TaxRateResponse> list(
            @RequestParam(name = "market", required = false) final String market,
            @RequestParam(name = "category", required = false) final String category,
            @RequestParam(name = "status", required = false) final String status) {
        return administration.list(mapper.filter(market, category, status)).stream()
                .map(mapper::toResponse).toList();
    }

    @Operation(operationId = "proposeTaxRate", summary = "Propose a tax rate change")
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TaxRateResponse propose(@RequestBody final TaxRateRequest request,
                                   final Authentication authentication) {
        return mapper.toResponse(administration.propose(
                mapper.command(request, authentication)));
    }

    @Operation(operationId = "approveTaxRate", summary = "Approve a proposed tax rate")
    @PostMapping("/{id}/approve")
    public TaxRateResponse approve(@PathVariable("id") final String id,
                                   final Authentication authentication) {
        return mapper.toResponse(administration.approve(id, mapper.actor(authentication)));
    }

    @Operation(operationId = "rejectTaxRate", summary = "Reject a proposed tax rate")
    @PostMapping("/{id}/reject")
    public TaxRateResponse reject(@PathVariable("id") final String id,
                                  final Authentication authentication) {
        return mapper.toResponse(administration.reject(id, mapper.actor(authentication)));
    }
}
