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

    private static final String DTC_SCHEMA_PATH = "/schemas/dtc-reading.schema.json";

    private final JsonSchema maintenanceEventSchema;
    private final JsonSchema dtcReadingSchema;

    public SchemaValidationService() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        
        try (InputStream is = getClass().getResourceAsStream(SCHEMA_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Contract schema not found on classpath: " + SCHEMA_PATH);
            }
            this.maintenanceEventSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load maintenance contract schema", e);
        }

        try (InputStream is = getClass().getResourceAsStream(DTC_SCHEMA_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Contract schema not found on classpath: " + DTC_SCHEMA_PATH);
            }
            this.dtcReadingSchema = factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load dtc reading schema", e);
        }
    }

    /**
     * Validates a single JSON event node against the maintenance-event schema.
     */
    public Set<ValidationMessage> validate(JsonNode event) {
        return maintenanceEventSchema.validate(event);
    }

    /**
     * Validates a single JSON event node against the dtc-reading schema.
     */
    public Set<ValidationMessage> validateDtc(JsonNode event) {
        return dtcReadingSchema.validate(event);
    }
}
