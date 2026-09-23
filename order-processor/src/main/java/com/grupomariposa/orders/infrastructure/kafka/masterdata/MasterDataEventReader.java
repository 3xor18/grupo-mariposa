package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.MarketCode;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import java.io.IOException;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class MasterDataEventReader {

    private static final Pattern CLIENT_ID = Pattern.compile("^CLI-[A-Z0-9]{1,20}$");
    private static final Pattern PRODUCT_ID = Pattern.compile("^PRD-[A-Z0-9]{1,20}$");
    private static final long MIN_VERSION = 1L;
    private static final String UNREADABLE = "payload is not a readable JSON object";
    private static final String INVALID = "%s is missing or invalid";
    private static final String CLIENT_ID_FIELD = "clientId";
    private static final String PRODUCT_ID_FIELD = "productId";
    private static final String VERSION_FIELD = "version";
    private static final String MARKET_FIELD = "market";
    private static final String STATUS_FIELD = "status";
    private static final String SEGMENT_FIELD = "segment";
    private static final String TAX_REGIME_FIELD = "taxRegime";
    private static final String TAX_CATEGORY_FIELD = "taxCategory";

    private final ObjectMapper objectMapper;

    public MasterDataEventReader(final ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public MasterDataChange readClient(final byte[] payload) {
        final ClientChangedMessage message = parse(payload, ClientChangedMessage.class);
        final String clientId = identifier(message.clientId(), CLIENT_ID, CLIENT_ID_FIELD);
        final long version = version(message.version());
        if (anyMissing(message.status(), message.segment(), message.taxRegime(),
                message.market())) {
            return new MasterDataChange.ClientRemoved(clientId, version);
        }
        return new MasterDataChange.ClientChanged(new Versioned<>(new ClientProfile(clientId,
                message.name(), enumOf(ClientStatus.class, message.status(), STATUS_FIELD),
                enumOf(ClientSegment.class, message.segment(), SEGMENT_FIELD),
                enumOf(TaxRegime.class, message.taxRegime(), TAX_REGIME_FIELD),
                market(message.market())), version));
    }

    public MasterDataChange readProduct(final byte[] payload) {
        final ProductChangedMessage message = parse(payload, ProductChangedMessage.class);
        final String productId = identifier(message.productId(), PRODUCT_ID, PRODUCT_ID_FIELD);
        final MarketCode market = market(message.market());
        final long version = version(message.version());
        if (anyMissing(message.status(), message.taxCategory())) {
            return new MasterDataChange.ProductRemoved(market, productId, version);
        }
        return new MasterDataChange.ProductChanged(market, new Versioned<>(new ProductProfile(
                productId, message.name(), message.sku(),
                enumOf(ProductStatus.class, message.status(), STATUS_FIELD),
                enumOf(TaxCategory.class, message.taxCategory(), TAX_CATEGORY_FIELD)),
                version));
    }

    private <M> M parse(final byte[] payload, final Class<M> type) {
        if (payload == null) {
            throw new MalformedChangeEvent(UNREADABLE);
        }
        try {
            final M message = objectMapper.readValue(payload, type);
            if (message == null) {
                throw new MalformedChangeEvent(UNREADABLE);
            }
            return message;
        } catch (IOException unreadable) {
            throw new MalformedChangeEvent(UNREADABLE);
        }
    }

    private static String identifier(final String value, final Pattern format,
                                     final String field) {
        if (value == null || !format.matcher(value).matches()) {
            throw invalid(field);
        }
        return value;
    }

    private static long version(final Long version) {
        if (version == null || version < MIN_VERSION) {
            throw invalid(VERSION_FIELD);
        }
        return version;
    }

    private static MarketCode market(final String value) {
        return MarketCode.parse(value).orElseThrow(() -> invalid(MARKET_FIELD));
    }

    private static <E extends Enum<E>> E enumOf(final Class<E> type, final String value,
                                                final String field) {
        return Stream.of(type.getEnumConstants()).filter(constant -> constant.name().equals(value))
                .findFirst().orElseThrow(() -> invalid(field));
    }

    private static boolean anyMissing(final String... values) {
        return Stream.of(values).anyMatch(Objects::isNull);
    }

    private static MalformedChangeEvent invalid(final String field) {
        return new MalformedChangeEvent(String.format(INVALID, field));
    }
}
