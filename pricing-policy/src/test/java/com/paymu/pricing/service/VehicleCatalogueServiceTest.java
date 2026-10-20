package com.paymu.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.pricing.model.VehicleCatalogueEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VehicleCatalogueService Tests")
class VehicleCatalogueServiceTest {

    private VehicleCatalogueService catalogueService;

    @BeforeEach
    void setUp() {
        catalogueService = new VehicleCatalogueService(new ObjectMapper());
        catalogueService.init();
    }

    @Test
    @DisplayName("Catalogue loader loads and validates all 6 required indicative segments")
    void loadsAndValidatesSixSegments() {
        List<VehicleCatalogueEntry> entries = catalogueService.getCatalogue();
        assertNotNull(entries);
        assertEquals(6, entries.size(), "Catalogue must have exactly 6 entries");

        assertEntry(entries, "hatchback_compact", "Compact hatchback", 800000);
        assertEntry(entries, "sedan_entry", "Entry sedan", 900000);
        assertEntry(entries, "suv_compact", "Compact SUV", 1000000);
        assertEntry(entries, "sedan_mid", "Mid-size sedan", 1200000);
        assertEntry(entries, "suv_premium", "Premium SUV", 1500000);
        assertEntry(entries, "suv_luxury", "Luxury SUV", 1800000);
    }

    @Test
    @DisplayName("findById returns entry for known segment and empty for unknown")
    void findByIdTests() {
        Optional<VehicleCatalogueEntry> sedanMid = catalogueService.findById("sedan_mid");
        assertTrue(sedanMid.isPresent());
        assertEquals(1200000, sedanMid.get().exShowroomPriceInr());

        Optional<VehicleCatalogueEntry> unknown = catalogueService.findById("non_existent_segment");
        assertTrue(unknown.isEmpty());

        Optional<VehicleCatalogueEntry> nullSegment = catalogueService.findById(null);
        assertTrue(nullSegment.isEmpty());
    }

    @Test
    @DisplayName("Catalogue loader fails fast on duplicate segment id")
    void failsFastOnDuplicateId() {
        String json = """
        [
          {"id": "sedan_mid", "label": "Mid sedan A", "ex_showroom_price_inr": 1200000},
          {"id": "sedan_mid", "label": "Mid sedan B", "ex_showroom_price_inr": 1300000}
        ]
        """;
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> catalogueService.loadFromString(json));
        assertTrue(ex.getMessage().contains("Duplicate catalogue segment id"), "Message should mention duplicate id: " + ex.getMessage());
    }

    @Test
    @DisplayName("Catalogue loader fails fast on price <= 0")
    void failsFastOnPriceZeroOrNegative() {
        String jsonZero = """
        [
          {"id": "sedan_mid", "label": "Mid sedan", "ex_showroom_price_inr": 0}
        ]
        """;
        assertThrows(IllegalStateException.class, () -> catalogueService.loadFromString(jsonZero));

        String jsonNegative = """
        [
          {"id": "sedan_mid", "label": "Mid sedan", "ex_showroom_price_inr": -1000}
        ]
        """;
        assertThrows(IllegalStateException.class, () -> catalogueService.loadFromString(jsonNegative));
    }

    @Test
    @DisplayName("Catalogue loader fails fast on empty list")
    void failsFastOnEmptyList() {
        String json = "[]";
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> catalogueService.loadFromString(json));
        assertTrue(ex.getMessage().contains("empty") || ex.getMessage().contains("failed schema validation"),
                "Message should indicate empty catalogue: " + ex.getMessage());
    }

    private void assertEntry(List<VehicleCatalogueEntry> entries, String id, String label, long price) {
        VehicleCatalogueEntry entry = entries.stream()
                .filter(e -> e.id().equals(id))
                .findFirst()
                .orElse(null);
        assertNotNull(entry, "Missing segment: " + id);
        assertEquals(label, entry.label());
        assertEquals(price, entry.exShowroomPriceInr());
    }
}
