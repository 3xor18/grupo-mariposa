package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.infrastructure.cache.CacheWrite;
import com.grupomariposa.orders.infrastructure.cache.MasterDataCacheUpdater;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class MasterDataChangeListenerTest {

    private final MasterDataCacheUpdater updater = mock(MasterDataCacheUpdater.class);
    private final MasterDataChangeListener listener = new MasterDataChangeListener(
            new MasterDataEventReader(new ObjectMapper()), updater);

    @Test
    void should_apply_client_and_product_changes() {
        when(updater.clientChanged(any())).thenReturn(CacheWrite.APPLIED);
        when(updater.productChanged(eq(Markets.CL), any())).thenReturn(CacheWrite.STALE);

        listener.onClientChanged(record("clients.changed.v1", MasterDataEventReaderTest.CLIENT));
        listener.onProductChanged(record("products.changed.v1",
                MasterDataEventReaderTest.PRODUCT));

        verify(updater).clientChanged(any());
        verify(updater).productChanged(eq(Markets.CL), any());
    }

    @Test
    void should_skip_malformed_events_without_throwing() {
        assertThatCode(() -> {
            listener.onClientChanged(record("clients.changed.v1", "{not json"));
            listener.onProductChanged(record("products.changed.v1", "{\"productId\":\"X\"}"));
        }).doesNotThrowAnyException();

        verify(updater).clientChangeIgnored();
        verify(updater).productChangeIgnored();
        verify(updater, never()).clientRemoved(any(), anyLong());
    }

    private static ConsumerRecord<String, byte[]> record(final String topic, final String json) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", MasterDataEventReaderTest.bytes(json));
    }
}
