package com.grupomariposa.orders.infrastructure.observability;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

public final class KafkaHealthIndicator implements HealthIndicator {

    private static final String NODES = "nodes";
    private static final String ERROR = "error";

    private final AdminClient adminClient;
    private final Duration timeout;

    public KafkaHealthIndicator(final AdminClient adminClient, final Duration timeout) {
        this.adminClient = Objects.requireNonNull(adminClient, "adminClient");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
    }

    @Override
    public Health health() {
        try {
            final int nodes = adminClient.describeCluster(new DescribeClusterOptions()
                            .timeoutMs(Math.toIntExact(timeout.toMillis())))
                    .nodes().get(timeout.toMillis(), TimeUnit.MILLISECONDS).size();
            return Health.up().withDetail(NODES, nodes).build();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail(ERROR, interrupted.getClass().getSimpleName()).build();
        } catch (ExecutionException | TimeoutException unavailable) {
            return Health.down().withDetail(ERROR, unavailable.getClass().getSimpleName()).build();
        }
    }
}
