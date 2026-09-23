package com.grupomariposa.orders.infrastructure.kafka.masterdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grupomariposa.orders.domain.Markets;
import com.grupomariposa.orders.domain.model.ClientProfile;
import com.grupomariposa.orders.domain.model.ClientSegment;
import com.grupomariposa.orders.domain.model.ClientStatus;
import com.grupomariposa.orders.domain.model.ProductProfile;
import com.grupomariposa.orders.domain.model.ProductStatus;
import com.grupomariposa.orders.domain.model.TaxCategory;
import com.grupomariposa.orders.domain.model.TaxRegime;
import com.grupomariposa.orders.infrastructure.masterdata.Versioned;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MasterDataEventReaderTest {

    static final String CLIENT = """
            {"eventId":"e-1","occurredAt":"2026-09-23T10:00:00Z","clientId":"CLI-99821",
             "version":4,"status":"BLOCKED","segment":"WHOLESALE","taxRegime":"GENERAL",
             "market":"MX","name":"Distribuidora Central","extra":true}""";
    static final String PRODUCT = """
            {"eventId":"e-2","occurredAt":"2026-09-23T10:00:00Z","productId":"PRD-001",
             "market":"CL","version":9,"status":"DISCONTINUED","taxCategory":"REDUCED",
             "sku":"BEB-600-PET"}""";

    private final MasterDataEventReader reader = new MasterDataEventReader(new ObjectMapper());

    @Test
    void should_read_full_client_state_with_its_version() {
        assertThat(reader.readClient(bytes(CLIENT))).isEqualTo(
                new MasterDataChange.ClientChanged(new Versioned<>(new ClientProfile(
                        "CLI-99821", "Distribuidora Central", ClientStatus.BLOCKED,
                        ClientSegment.WHOLESALE, TaxRegime.GENERAL, Markets.MX), 4L)));
    }

    @Test
    void should_read_full_product_state_keyed_by_market() {
        assertThat(reader.readProduct(bytes(PRODUCT))).isEqualTo(
                new MasterDataChange.ProductChanged(Markets.CL, new Versioned<>(
                        new ProductProfile("PRD-001", null, "BEB-600-PET",
                                ProductStatus.DISCONTINUED, TaxCategory.REDUCED), 9L)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "segment", "taxRegime", "market"})
    void should_evict_clients_when_the_event_lacks_full_state(final String field) {
        assertThat(reader.readClient(bytes(CLIENT.replace("\"" + field + "\"", "\"x" + field
                + "\"")))).isEqualTo(new MasterDataChange.ClientRemoved("CLI-99821", 4L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "taxCategory"})
    void should_evict_products_when_the_event_lacks_full_state(final String field) {
        assertThat(reader.readProduct(bytes(PRODUCT.replace("\"" + field + "\"", "\"x" + field
                + "\"")))).isEqualTo(
                        new MasterDataChange.ProductRemoved(Markets.CL, "PRD-001", 9L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "null", "[]", "\"CLI-1\""})
    void should_reject_unreadable_payloads(final String payload) {
        assertThatThrownBy(() -> reader.readClient(bytes(payload)))
                .isInstanceOf(MalformedChangeEvent.class).hasMessageContaining("JSON");
        assertThatThrownBy(() -> reader.readProduct(null))
                .isInstanceOf(MalformedChangeEvent.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"clientId\":\"CLI-99821\"|\"clientId\":\"cli-1\"|clientId",
        "\"clientId\":\"CLI-99821\"|\"clientId\":null|clientId",
        "\"version\":4|\"version\":0|version",
        "\"version\":4|\"version\":null|version",
        "\"status\":\"BLOCKED\"|\"status\":\"DELETED\"|status",
        "\"segment\":\"WHOLESALE\"|\"segment\":\"VIP\"|segment",
        "\"taxRegime\":\"GENERAL\"|\"taxRegime\":\"OTHER\"|taxRegime",
        "\"market\":\"MX\"|\"market\":\"MEX\"|market"})
    void should_reject_invalid_client_fields(final String replacement) {
        final String[] parts = replacement.split("\\|");

        assertThatThrownBy(() -> reader.readClient(bytes(CLIENT.replace(parts[0], parts[1]))))
                .isInstanceOf(MalformedChangeEvent.class).hasMessageContaining(parts[2]);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "\"productId\":\"PRD-001\"|\"productId\":\"X\"|productId",
        "\"market\":\"CL\"|\"market\":null|market",
        "\"status\":\"DISCONTINUED\"|\"status\":\"GONE\"|status",
        "\"taxCategory\":\"REDUCED\"|\"taxCategory\":\"ZERO\"|taxCategory"})
    void should_reject_invalid_product_fields(final String replacement) {
        final String[] parts = replacement.split("\\|");

        assertThatThrownBy(() -> reader.readProduct(bytes(PRODUCT.replace(parts[0], parts[1]))))
                .isInstanceOf(MalformedChangeEvent.class).hasMessageContaining(parts[2]);
    }

    static byte[] bytes(final String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }
}
