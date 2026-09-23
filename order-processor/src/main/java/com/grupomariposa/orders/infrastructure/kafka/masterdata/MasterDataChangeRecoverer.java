package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import com.grupomariposa.orders.infrastructure.cache.MasterDataCacheUpdater;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;

public final class MasterDataChangeRecoverer implements ConsumerRecordRecoverer {

    private static final Logger LOG = LoggerFactory.getLogger(MasterDataChangeRecoverer.class);

    private final MasterDataCacheUpdater updater;
    private final String clientsTopic;

    public MasterDataChangeRecoverer(final MasterDataCacheUpdater updater,
                                     final String clientsTopic) {
        this.updater = Objects.requireNonNull(updater, "updater");
        this.clientsTopic = Objects.requireNonNull(clientsTopic, "clientsTopic");
    }

    @Override
    public void accept(final ConsumerRecord<?, ?> consumerRecord, final Exception cause) {
        if (clientsTopic.equals(consumerRecord.topic())) {
            updater.clientChangeFailed();
        } else {
            updater.productChangeFailed();
        }
        LOG.error("Skipped master data change {}-{}@{} after retries: {}", consumerRecord.topic(),
                consumerRecord.partition(), consumerRecord.offset(),
                cause.getClass().getSimpleName());
    }
}
