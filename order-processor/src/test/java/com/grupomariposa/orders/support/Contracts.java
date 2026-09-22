package com.grupomariposa.orders.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

public final class Contracts {

    public static final Path ROOT = Path.of("..", "contracts");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory());
    private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";

    private Contracts() {
    }

    public static JsonNode json(final String relativePath) {
        return read(JSON, relativePath);
    }

    public static JsonNode yaml(final String relativePath) {
        return read(YAML, relativePath);
    }

    public static byte[] bytes(final String relativePath) {
        try {
            return Files.readAllBytes(ROOT.resolve(relativePath));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static Set<ValidationMessage> validateEvent(final String schemaPath,
                                                       final JsonNode payload) {
        return factory().getSchema(json(schemaPath)).validate(payload);
    }

    public static Set<ValidationMessage> validateOpenApiSchema(final String openApiPath,
                                                               final String schemaName,
                                                               final JsonNode payload) {
        final JsonNode document = yaml(openApiPath);
        final ObjectNode schema = JSON.createObjectNode();
        schema.put("$schema", DIALECT);
        schema.put("$ref", "#/components/schemas/" + schemaName);
        schema.set("components", document.get("components"));
        final JsonSchema compiled = factory().getSchema(schema);
        return compiled.validate(payload);
    }

    public static JsonNode openApiExample(final String openApiPath, final String path) {
        return yaml(openApiPath).path("paths").path(path).path("get").path("responses")
                .path("200").path("content").path("application/json").path("example");
    }

    private static JsonSchemaFactory factory() {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    private static JsonNode read(final ObjectMapper mapper, final String relativePath) {
        try {
            return mapper.readTree(ROOT.resolve(relativePath).toFile());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }
}
