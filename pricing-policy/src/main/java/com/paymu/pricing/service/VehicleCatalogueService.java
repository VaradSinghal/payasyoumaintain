package com.paymu.pricing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.paymu.pricing.model.VehicleCatalogueEntry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * Service that loads and validates the indicative vehicle catalogue at startup.
 */
@Service
public class VehicleCatalogueService {

    private static final Logger log = LoggerFactory.getLogger(VehicleCatalogueService.class);
    private static final String CATALOGUE_RESOURCE = "/vehicle-catalogue.json";
    private static final String SCHEMA_RESOURCE = "/schemas/vehicle-catalogue.schema.json";

    private final ObjectMapper objectMapper;
    private List<VehicleCatalogueEntry> catalogue = List.of();
    private Map<String, VehicleCatalogueEntry> catalogueMap = Map.of();

    public VehicleCatalogueService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        try (InputStream catStream = getClass().getResourceAsStream(CATALOGUE_RESOURCE)) {
            if (catStream == null) {
                throw new IllegalStateException("Vehicle catalogue resource not found: " + CATALOGUE_RESOURCE);
            }
            loadFromStream(catStream);
        } catch (Exception e) {
            log.error("Failed to load or validate indicative vehicle catalogue: {}", e.getMessage(), e);
            throw (e instanceof IllegalStateException ise) ? ise : new IllegalStateException("Startup failed: vehicle catalogue could not be initialized", e);
        }
    }

    public void loadFromString(String json) {
        try {
            JsonNode catNode = objectMapper.readTree(json);
            loadFromNode(catNode);
        } catch (Exception e) {
            throw (e instanceof IllegalStateException ise) ? ise : new IllegalStateException("Failed to parse vehicle catalogue: " + e.getMessage(), e);
        }
    }

    public void loadFromStream(InputStream catStream) {
        try {
            JsonNode catNode = objectMapper.readTree(catStream);
            loadFromNode(catNode);
        } catch (Exception e) {
            throw (e instanceof IllegalStateException ise) ? ise : new IllegalStateException("Failed to parse vehicle catalogue: " + e.getMessage(), e);
        }
    }

    public void loadFromNode(JsonNode catNode) {
        try (InputStream schemaStream = getClass().getResourceAsStream(SCHEMA_RESOURCE)) {
            if (schemaStream == null) {
                throw new IllegalStateException("Vehicle catalogue schema not found: " + SCHEMA_RESOURCE);
            }

            JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            JsonSchema schema = schemaFactory.getSchema(schemaStream);

            Set<ValidationMessage> validationMessages = schema.validate(catNode);
            if (!validationMessages.isEmpty()) {
                throw new IllegalStateException("Vehicle catalogue failed schema validation: " + validationMessages);
            }

            List<VehicleCatalogueEntry> entries = objectMapper.convertValue(
                    catNode,
                    new TypeReference<List<VehicleCatalogueEntry>>() {}
            );

            if (entries == null || entries.isEmpty()) {
                throw new IllegalStateException("Vehicle catalogue cannot be empty");
            }

            Map<String, VehicleCatalogueEntry> map = new LinkedHashMap<>();
            for (VehicleCatalogueEntry entry : entries) {
                if (entry.exShowroomPriceInr() <= 0) {
                    throw new IllegalStateException("ex_showroom_price_inr must be > 0: " + entry.id());
                }
                if (map.put(entry.id(), entry) != null) {
                    throw new IllegalStateException("Duplicate catalogue segment id: " + entry.id());
                }
            }

            this.catalogue = Collections.unmodifiableList(entries);
            this.catalogueMap = Collections.unmodifiableMap(map);

            log.info("Loaded and validated {} indicative vehicle catalogue entries", this.catalogue.size());
        } catch (Exception e) {
            throw (e instanceof IllegalStateException ise) ? ise : new IllegalStateException("Failed to load vehicle catalogue: " + e.getMessage(), e);
        }
    }

    public List<VehicleCatalogueEntry> getCatalogue() {
        return catalogue;
    }

    public Optional<VehicleCatalogueEntry> findById(String segmentId) {
        if (segmentId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(catalogueMap.get(segmentId));
    }
}
