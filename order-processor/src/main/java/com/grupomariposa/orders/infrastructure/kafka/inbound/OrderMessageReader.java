package com.grupomariposa.orders.infrastructure.kafka.inbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.grupomariposa.orders.application.error.ErrorCategory;
import java.io.IOException;
import java.util.stream.Collectors;

public final class OrderMessageReader {

    private static final String EMPTY = "Payload is empty";
    private static final String NOT_JSON = "Payload is not valid JSON";
    private static final String NOT_OBJECT = "Payload is not a JSON object";
    private static final String SCHEMA = "Payload does not match orders.created.v1 at %s";
    private static final String ROOT = "$";
    private static final String DOT = ".";
    private static final String INDEX = "[%d]";

    private final ObjectMapper mapper;

    public OrderMessageReader() {
        this.mapper = strictMapper();
    }

    public OrderCreatedMessage read(final byte[] payload) {
        final JsonNode tree = parseTree(payload);
        final MessageIds ids = MessageIds.from(tree);
        try {
            return mapper.treeToValue(tree, OrderCreatedMessage.class);
        } catch (JsonMappingException mismatch) {
            throw failure(SCHEMA.formatted(pathOf(mismatch)), ids, mismatch);
        } catch (JsonProcessingException mismatch) {
            throw failure(SCHEMA.formatted(ROOT), ids, mismatch);
        }
    }

    private JsonNode parseTree(final byte[] payload) {
        if (payload == null || payload.length == 0) {
            throw failure(EMPTY, MessageIds.UNKNOWN, null);
        }
        final JsonNode tree;
        try {
            tree = mapper.readTree(payload);
        } catch (IOException unreadable) {
            throw failure(NOT_JSON, MessageIds.UNKNOWN, unreadable);
        }
        if (tree == null || !tree.isObject()) {
            throw failure(NOT_OBJECT, MessageIds.UNKNOWN, null);
        }
        return tree;
    }

    private static String pathOf(final JsonMappingException mismatch) {
        final String path = mismatch.getPath().stream()
                .map(reference -> reference.getFieldName() != null
                        ? DOT + reference.getFieldName()
                        : INDEX.formatted(reference.getIndex()))
                .collect(Collectors.joining());
        return path.isEmpty() ? ROOT : path.substring(path.startsWith(DOT) ? 1 : 0);
    }

    private static RecordProcessingFailure failure(final String cause, final MessageIds ids,
                                                   final Throwable origin) {
        return RecordProcessingFailure.of(ErrorCategory.DESERIALIZATION, cause, ids, null,
                origin);
    }

    private static ObjectMapper strictMapper() {
        final JsonMapper mapper = JsonMapper.builder()
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .build();
        mapper.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Integer)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        mapper.coercionConfigFor(LogicalType.Float)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail);
        return mapper;
    }
}
