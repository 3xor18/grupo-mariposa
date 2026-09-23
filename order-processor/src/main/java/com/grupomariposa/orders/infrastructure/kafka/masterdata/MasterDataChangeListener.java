package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import com.grupomariposa.orders.infrastructure.cache.CacheWrite;
import com.grupomariposa.orders.infrastructure.cache.MasterDataCacheUpdater;
import java.util.Objects;
import java.util.function.Function;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;

public final class MasterDataChangeListener {

    public static final String CLIENTS_LISTENER_ID = "clients-changed-listener";
    public static final String PRODUCTS_LISTENER_ID = "products-changed-listener";
    private static final String WRITE_FAILED = "Cache update failed for %s-%d@%d";
    private static final Logger LOG = LoggerFactory.getLogger(MasterDataChangeListener.class);

    private final MasterDataEventReader reader;
    private final MasterDataCacheUpdater updater;

    public MasterDataChangeListener(final MasterDataEventReader reader,
                                    final MasterDataCacheUpdater updater) {
        this.reader = Objects.requireNonNull(reader, "reader");
        this.updater = Objects.requireNonNull(updater, "updater");
    }

    @KafkaListener(id = CLIENTS_LISTENER_ID,
            containerFactory = MasterDataListenerSettings.CONTAINER_FACTORY,
            topics = "#{@masterDataListenerSettings.clientsTopic()}",
            groupId = "#{@masterDataListenerSettings.groupId()}",
            concurrency = "#{@masterDataListenerSettings.concurrency()}",
            autoStartup = "#{@masterDataListenerSettings.autoStartup()}")
    public void onClientChanged(final ConsumerRecord<String, byte[]> consumerRecord) {
        handle(consumerRecord, reader::readClient, updater::clientChangeIgnored);
    }

    @KafkaListener(id = PRODUCTS_LISTENER_ID,
            containerFactory = MasterDataListenerSettings.CONTAINER_FACTORY,
            topics = "#{@masterDataListenerSettings.productsTopic()}",
            groupId = "#{@masterDataListenerSettings.groupId()}",
            concurrency = "#{@masterDataListenerSettings.concurrency()}",
            autoStartup = "#{@masterDataListenerSettings.autoStartup()}")
    public void onProductChanged(final ConsumerRecord<String, byte[]> consumerRecord) {
        handle(consumerRecord, reader::readProduct, updater::productChangeIgnored);
    }

    private void handle(final ConsumerRecord<String, byte[]> consumerRecord,
                        final Function<byte[], MasterDataChange> read, final Runnable ignored) {
        final MasterDataChange change;
        try {
            change = read.apply(consumerRecord.value());
        } catch (MalformedChangeEvent malformed) {
            ignored.run();
            LOG.warn("Ignored malformed master data change {}-{}@{}: {}", consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset(), malformed.getMessage());
            return;
        }
        final CacheWrite result = change.applyTo(updater);
        if (result == CacheWrite.FAILED) {
            throw new CacheUpdateFailure(WRITE_FAILED.formatted(consumerRecord.topic(),
                    consumerRecord.partition(), consumerRecord.offset()));
        }
        LOG.debug("Master data change {}-{}@{} {}", consumerRecord.topic(),
                consumerRecord.partition(), consumerRecord.offset(), result);
    }
}
