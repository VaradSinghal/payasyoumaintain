package com.paymu.identity.service;

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

@Service
public class SchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationService.class);
    private static final String REGISTRATION_SCHEMA_PATH = "/schemas/vehicle-registration-request.schema.json";

    private final JsonSchema registrationSchema;

    public SchemaValidationService() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(REGISTRATION_SCHEMA_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Schema not found on classpath: " + REGISTRATION_SCHEMA_PATH);
            }
            this.registrationSchema = factory.getSchema(is);
            log.info("Loaded vehicle-registration-request schema from {}", REGISTRATION_SCHEMA_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load schema " + REGISTRATION_SCHEMA_PATH, e);
        }
    }

    public Set<ValidationMessage> validateRegistration(JsonNode node) {
        return registrationSchema.validate(node);
    }
}
