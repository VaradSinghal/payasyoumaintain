package com.paymu.maintenance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;

/**
 * Validates JSON payloads against the shared contract schema
 * ({@code contracts/schemas/maintenance-event.schema.json}).
 *
 * <p>Schema is copied onto the classpath at build time by the
 * {@code maven-resources-plugin} — no manually maintained copy.</p>
 */
@Service
public class SchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationService.class);
    private static final String SCHEMA_PATH = "/schemas/maintenance-event.schema.json";

    private final JsonSchema maintenanceEventSchema;

    public SchemaValidationService() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Contract schema not found on classpath: " + SCHEMA_PATH
                                + ". Ensure the maven-resources-plugin has run (mvn generate-resources).");
            }
            this.maintenanceEventSchema = factory.getSchema(is);
            log.info("Loaded maintenance-event contract schema from classpath: {}", SCHEMA_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load contract schema", e);
        }
    }

    /**
     * Validates a single JSON event node against the maintenance-event schema.
     *
     * @param event the raw JSON node to validate
     * @return a set of validation messages; empty if the event is valid
     */
    public Set<ValidationMessage> validate(JsonNode event) {
        return maintenanceEventSchema.validate(event);
    }
}
