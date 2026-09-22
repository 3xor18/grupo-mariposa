package com.grupomariposa.orders.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.infrastructure.crypto.AesGcmPiiCipher;
import com.grupomariposa.orders.infrastructure.crypto.PiiProperties;
import com.grupomariposa.orders.infrastructure.kafka.MessagingProperties;
import com.grupomariposa.orders.infrastructure.persistence.IndexInitializer;
import com.grupomariposa.orders.infrastructure.persistence.MongoOrderQueryRepository;
import com.grupomariposa.orders.infrastructure.persistence.MongoOrderStore;
import com.grupomariposa.orders.infrastructure.persistence.MongoOutboxStore;
import com.grupomariposa.orders.infrastructure.persistence.PersistenceProperties;
import com.grupomariposa.orders.infrastructure.persistence.TransactionRunner;
import com.grupomariposa.orders.infrastructure.persistence.mapping.OrderDocumentMapper;
import com.grupomariposa.orders.infrastructure.persistence.outbox.OutboxPayloadFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    @Bean
    public MongoTransactionManager transactionManager(final MongoDatabaseFactory factory) {
        return new MongoTransactionManager(factory);
    }

    @Bean
    public TransactionRunner transactionRunner(final MongoTransactionManager transactionManager,
                                               final PersistenceProperties properties) {
        return new TransactionRunner(new TransactionTemplate(transactionManager),
                properties.transactionAttempts(), properties.transactionRetryBackoff());
    }

    @Bean
    public AesGcmPiiCipher piiCipher(final PiiProperties properties) {
        return new AesGcmPiiCipher(properties);
    }

    @Bean
    public OrderDocumentMapper orderDocumentMapper(final AesGcmPiiCipher cipher) {
        return new OrderDocumentMapper(cipher);
    }

    @Bean
    public MongoOrderStore orderStore(final MongoTemplate mongoTemplate,
                                      final TransactionRunner transactions,
                                      final OrderDocumentMapper mapper,
                                      final ObjectMapper objectMapper,
                                      final MessagingProperties messaging) {
        return new MongoOrderStore(mongoTemplate, transactions, mapper,
                new OutboxPayloadFactory(objectMapper), messaging.topics().ordersProcessed());
    }

    @Bean
    public MongoOrderQueryRepository orderQueryRepository(final MongoTemplate mongoTemplate,
                                                          final OrderDocumentMapper mapper) {
        return new MongoOrderQueryRepository(mongoTemplate, mapper);
    }

    @Bean
    public MongoOutboxStore outboxStore(final MongoTemplate mongoTemplate) {
        return new MongoOutboxStore(mongoTemplate);
    }

    @Bean
    public IndexInitializer indexInitializer(final MongoTemplate mongoTemplate,
                                             final PersistenceProperties properties) {
        return new IndexInitializer(mongoTemplate, properties);
    }
}
