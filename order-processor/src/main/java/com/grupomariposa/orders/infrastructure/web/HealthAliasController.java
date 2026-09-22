package com.grupomariposa.orders.infrastructure.web;

import java.util.Map;
import java.util.Objects;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = HealthAliasController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class HealthAliasController {

    public static final String BASE_PATH = "/health";
    private static final String LIVENESS_GROUP = "liveness";
    private static final String READINESS_GROUP = "readiness";
    private static final String STATUS = "status";

    private final HealthEndpoint healthEndpoint;

    public HealthAliasController(final HealthEndpoint healthEndpoint) {
        this.healthEndpoint = Objects.requireNonNull(healthEndpoint, "healthEndpoint");
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return statusOf(LIVENESS_GROUP);
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> ready() {
        return statusOf(READINESS_GROUP);
    }

    private ResponseEntity<Map<String, String>> statusOf(final String group) {
        final HealthComponent health = healthEndpoint.healthForPath(group);
        final Status status = health == null ? Status.UNKNOWN : health.getStatus();
        final HttpStatus httpStatus = Status.UP.equals(status)
                ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(httpStatus).body(Map.of(STATUS, status.getCode()));
    }
}
