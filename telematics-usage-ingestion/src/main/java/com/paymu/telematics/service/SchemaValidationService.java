package com.paymu.telematics.service;

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
 * ({@code contracts/schemas/usage-event.schema.json}).
 *
 * <p>The schema is copied onto the classpath at build time by the
 * {@code maven-resources-plugin} — see {@code pom.xml}.  There is no
 * manually maintained copy in {@code src/main/resources}.</p>
 */
@Service
public class SchemaValidationService {

    private static final Logger log = LoggerFactory.getLogger(SchemaValidationService.class);
    private static final String USAGE_EVENT_SCHEMA_PATH = "/schemas/usage-event.schema.json";
    private static final String TRIP_AGGREGATE_SCHEMA_PATH = "/schemas/trip-aggregate.schema.json";

    private final JsonSchema usageEventSchema;
    private final JsonSchema tripAggregateSchema;

    public SchemaValidationService() {
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream is = getClass().getResourceAsStream(USAGE_EVENT_SCHEMA_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Contract schema not found on classpath: " + USAGE_EVENT_SCHEMA_PATH
                                + ". Ensure the maven-resources-plugin has run (mvn generate-resources).");
            }
            this.usageEventSchema = factory.getSchema(is);
            log.info("Loaded usage-event contract schema from classpath: {}", USAGE_EVENT_SCHEMA_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load usage-event contract schema", e);
        }

        try (InputStream is = getClass().getResourceAsStream(TRIP_AGGREGATE_SCHEMA_PATH)) {
            if (is == null) {
                throw new IllegalStateException(
                        "Contract schema not found on classpath: " + TRIP_AGGREGATE_SCHEMA_PATH
                                + ". Ensure the maven-resources-plugin has run (mvn generate-resources).");
            }
            this.tripAggregateSchema = factory.getSchema(is);
            log.info("Loaded trip-aggregate contract schema from classpath: {}", TRIP_AGGREGATE_SCHEMA_PATH);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load trip-aggregate contract schema", e);
        }
    }

    /**
     * Validates a single JSON event node against the usage-event schema.
     *
     * @param event the raw JSON node to validate
     * @return a set of validation messages; empty if the event is valid
     */
    public Set<ValidationMessage> validate(JsonNode event) {
        return usageEventSchema.validate(event);
    }

    /**
     * Validates a single JSON trip aggregate node against the trip-aggregate schema.
     *
     * @param aggregate the raw JSON node to validate
     * @return a set of validation messages; empty if the aggregate is valid
     */
    public Set<ValidationMessage> validateTripAggregate(JsonNode aggregate) {
        return tripAggregateSchema.validate(aggregate);
    }
}
