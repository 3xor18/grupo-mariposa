package com.grupomariposa.orders.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.infrastructure.cache.CacheMetrics;
import com.grupomariposa.orders.infrastructure.cache.CacheWrite;
import com.grupomariposa.orders.infrastructure.cache.VersionedCache;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import com.grupomariposa.orders.support.IntegrationTest;
import com.grupomariposa.orders.support.KafkaTestClient;
import com.grupomariposa.orders.support.OrderEvents;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

class MasterDataCacheIT extends IntegrationTest {

    private static final String CLIENTS_CHANGED = "clients.changed.v1";
    private static final String PRODUCTS_CHANGED = "products.changed.v1";
    private static final String VERSION_FIELD = "v";
    private static final String DATA_FIELD = "d";
    private static final Duration WAIT = Duration.ofSeconds(45);
    private static final Duration POLL = Duration.ofMillis(200);
    private static final int ITEM_QUANTITY = 24;
    private static final double ITEM_PRICE = 35.5;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MeterRegistry registry;

    @Autowired
    private VersionedCache<ClientProfile> clientCache;

    @Test
    void should_reject_orders_of_a_cached_client_blocked_by_a_newer_event() {
        stubs.golden();
        stubs.versionedClient("CLI-CACHEBLK1", "MX", "ACTIVE", 1);
        processOrder("BLK-A", "CLI-CACHEBLK1", "PRD-001", "APPROVED");
        assertThat(cachedVersion("clients:CLI-CACHEBLK1")).isEqualTo("1");

        publishClient("CLI-CACHEBLK1", 2, "BLOCKED");
        awaitCachedVersion("clients:CLI-CACHEBLK1", "2");
        assertThat((String) redis.opsForHash().get("clients:CLI-CACHEBLK1", DATA_FIELD))
                .contains("\"encryptedName\":\"k1:", "\"status\":\"BLOCKED\"");

        final Document rejected = processOrder("BLK-B", "CLI-CACHEBLK1", "PRD-001",
                "REJECTED");
        assertThat(rejected.getString("reason")).isEqualTo("CLIENT_NOT_ACTIVE");
    }

    @Test
    void should_ignore_change_events_older_than_the_cached_version() {
        stubs.golden();
        stubs.versionedClient("CLI-CACHESTALE1", "MX", "ACTIVE", 5);
        processOrder("STALE-A", "CLI-CACHESTALE1", "PRD-001", "APPROVED");
        final double staleBefore = invalidations("clients", "stale");

        publishClient("CLI-CACHESTALE1", 3, "BLOCKED");
        await().atMost(WAIT).pollInterval(POLL)
                .until(() -> invalidations("clients", "stale") > staleBefore);

        assertThat(cachedVersion("clients:CLI-CACHESTALE1")).isEqualTo("5");
        processOrder("STALE-B", "CLI-CACHESTALE1", "PRD-001", "APPROVED");
    }

    @Test
    void should_reject_products_discontinued_by_a_change_event() {
        stubs.golden();
        stubs.versionedProduct("PRD-CACHEDISC1", "MX", "ACTIVE", 1);
        processOrder("DISC-A", "CLI-99821", "PRD-CACHEDISC1", "APPROVED");

        publishProduct("PRD-CACHEDISC1", "MX", 2, "DISCONTINUED");
        awaitCachedVersion("products:MX:PRD-CACHEDISC1", "2");

        final Document rejected = processOrder("DISC-B", "CLI-99821", "PRD-CACHEDISC1",
                "REJECTED");
        assertThat(rejected.getString("reason")).isEqualTo("PRODUCT_NOT_ACTIVE");
    }

    @Test
    void should_not_let_an_older_api_read_override_a_newer_cached_version() {
        stubs.golden();
        stubs.versionedClient("CLI-CACHEOLD1", "MX", "ACTIVE", 4);
        processOrder("OLD-0", "CLI-CACHEOLD1", "PRD-001", "APPROVED");
        publishClient("CLI-CACHEOLD1", 7, "BLOCKED");
        awaitCachedVersion("clients:CLI-CACHEOLD1", "7");

        final ClientProfile olderRead = new ClientProfile("CLI-CACHEOLD1", null,
                ClientStatus.ACTIVE, ClientSegment.WHOLESALE, TaxRegime.GENERAL, Markets.MX);
        assertThat(clientCache.apply("CLI-CACHEOLD1", new Versioned<>(olderRead, 4)))
                .isEqualTo(CacheWrite.STALE);
        assertThat(cachedVersion("clients:CLI-CACHEOLD1")).isEqualTo("7");
        assertThat(processOrder("OLD-A", "CLI-CACHEOLD1", "PRD-001", "REJECTED")
                .getString("reason")).isEqualTo("CLIENT_NOT_ACTIVE");

        redis.opsForHash().delete("clients:CLI-CACHEOLD1", DATA_FIELD);
        processOrder("OLD-B", "CLI-CACHEOLD1", "PRD-001", "APPROVED");
        assertThat(cachedVersion("clients:CLI-CACHEOLD1")).isEqualTo("7");
        assertThat(redis.opsForHash().hasKey("clients:CLI-CACHEOLD1", DATA_FIELD)).isFalse();
    }

    private Document processOrder(final String label, final String clientId,
                                  final String productId, final String status) {
        final OrderEvents event = OrderEvents.goldenWithFreshIds(label).clientId(clientId)
                .singleItem(productId, ITEM_QUANTITY, ITEM_PRICE);
        try (KafkaTestClient kafka = kafka()) {
            kafka.send(ORDERS_CREATED, event.orderId(), event.bytes());
        }
        await().atMost(WAIT).pollInterval(POLL).until(() -> {
            final Document order = mongo.findById(event.orderId(), Document.class, "orders");
            return order != null && status.equals(order.getString("status"));
        });
        return mongo.findById(event.orderId(), Document.class, "orders");
    }

    private void publishClient(final String clientId, final long version, final String status) {
        publish(CLIENTS_CHANGED, clientId, """
                {"eventId":"%s-%d","occurredAt":"2026-09-23T10:00:00Z","clientId":"%s",
                 "version":%d,"status":"%s","segment":"WHOLESALE","taxRegime":"GENERAL",
                 "market":"MX"}"""
                .formatted(clientId, version, clientId, version, status));
    }

    private void publishProduct(final String productId, final String market,
                                final long version, final String status) {
        publish(PRODUCTS_CHANGED, market + ":" + productId, """
                {"eventId":"%s-%d","occurredAt":"2026-09-23T10:00:00Z","productId":"%s",
                 "market":"%s","version":%d,"status":"%s","taxCategory":"STANDARD"}"""
                .formatted(productId, version, productId, market, version, status));
    }

    private void publish(final String topic, final String key, final String json) {
        try (KafkaTestClient kafka = kafka()) {
            kafka.send(topic, key, json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void awaitCachedVersion(final String key, final String version) {
        await().atMost(WAIT).pollInterval(POLL)
                .until(() -> version.equals(cachedVersion(key)));
    }

    private String cachedVersion(final String key) {
        return (String) redis.opsForHash().get(key, VERSION_FIELD);
    }

    private double invalidations(final String cache, final String outcome) {
        return registry.counter(CacheMetrics.INVALIDATIONS, CacheMetrics.CACHE_TAG, cache,
                CacheMetrics.OUTCOME_TAG, outcome).count();
    }
}
