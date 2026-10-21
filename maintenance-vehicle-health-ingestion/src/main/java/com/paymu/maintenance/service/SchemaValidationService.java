package com.paymu.maintenance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Set;

/**
 * Validates JSON payloads against the shared contract schemas.
 *
 * <p>Schemas are copied onto the classpath at build time by the
 * {@code maven-resources-plugin} — no manually maintained copy.</p>
 */
@Service
public class SchemaValidationService {

    private static final String SCHEMA_PATH = "/schemas/maintenance-event.schema.json";
    private static final String DTC_SCHEMA_PATH = "/schemas/dtc-reading.schema.json";
    private static final String SERVICE_RECORD_REQUEST_SCHEMA_PATH = "/schemas/service-record-request.schema.json";
    private static final String SERVICE_RECORD_RESPONSE_SCHEMA_PATH = "/schemas/service-record-response.schema.json";

    private final JsonSchema maintenanceEventSchema;
    private final JsonSchema dtcReadingSchema;
    private final JsonSchema serviceRecordRequestSchema;
    private final JsonSchema serviceRecordResponseSchema;

    public SchemaValidationService() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        this.maintenanceEventSchema = loadSchema(factory, SCHEMA_PATH);
        this.dtcReadingSchema = loadSchema(factory, DTC_SCHEMA_PATH);
        this.serviceRecordRequestSchema = loadSchema(factory, SERVICE_RECORD_REQUEST_SCHEMA_PATH);
        this.serviceRecordResponseSchema = loadSchema(factory, SERVICE_RECORD_RESPONSE_SCHEMA_PATH);
    }

    private JsonSchema loadSchema(JsonSchemaFactory factory, String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalStateException("Contract schema not found on classpath: " + path);
            }
            return factory.getSchema(is);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load contract schema: " + path, e);
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

    /**
     * Validates a single JSON node against the service-record-request schema.
     */
    public Set<ValidationMessage> validateServiceRecordRequest(JsonNode node) {
        return serviceRecordRequestSchema.validate(node);
    }

    /**
     * Validates a single JSON node against the service-record-response schema.
     */
    public Set<ValidationMessage> validateServiceRecordResponse(JsonNode node) {
        return serviceRecordResponseSchema.validate(node);
    }
}
