package com.grupomariposa.orders.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CachePropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    static Stream<Duration> invalidTtls() {
        return Stream.of(null, Duration.ZERO, Duration.ofSeconds(-1));
    }

    @ParameterizedTest
    @MethodSource("invalidTtls")
    void should_reject_non_positive_ttls(final Duration ttl) {
        assertThat(ttlViolations(validator.validate(new ClientCacheProperties(true, ttl,
                "clients")))).isOne();
        assertThat(ttlViolations(validator.validate(new ProductCacheProperties(true, ttl,
                "products")))).isOne();
    }

    @Test
    void should_accept_positive_ttls() {
        assertThat(validator.validate(new ClientCacheProperties(true, Duration.ofSeconds(60),
                "clients"))).isEmpty();
        assertThat(validator.validate(new ProductCacheProperties(true, Duration.ofMillis(1),
                "products"))).isEmpty();
    }

    private static <T> long ttlViolations(final Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .filter(violation -> "ttl".equals(violation.getPropertyPath().toString()))
                .count();
    }
}
