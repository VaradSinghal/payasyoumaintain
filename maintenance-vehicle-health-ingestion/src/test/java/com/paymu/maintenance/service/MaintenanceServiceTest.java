package com.paymu.maintenance.service;

import com.paymu.maintenance.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link OcrStubService} and {@link MaintenanceStore}.
 */
class MaintenanceServiceTest {

    // ── OCR Stub Service ────────────────────────────────────────

    @Nested
    @DisplayName("OcrStubService")
    class OcrStubTests {

        private OcrStubService ocrStubService;

        @BeforeEach
        void setUp() {
            ocrStubService = new OcrStubService();
        }

        @Test
        @DisplayName("Echoes all typed fields into the extracted event")
        void echoesFields() {
            SelfUploadRequest request = new SelfUploadRequest(
                    UUID.randomUUID().toString(),
                    "2026-08-10",
                    "oil_change",
                    30000,
                    List.of(new PartReplaced("Oil Filter", "OF-2024A", 1)),
                    "Changed at local garage.",
                    "s3://paymu-docs/uploads/receipt.pdf");

            OcrResult result = ocrStubService.process(request);

            assertEquals("stub_echo", result.status());
            assertNotNull(result.extractedEvent());

            MaintenanceEvent event = result.extractedEvent();
            assertEquals(request.vehicleId(), event.vehicleId());
            assertEquals("2026-08-10", event.serviceDate());
            assertEquals("oil_change", event.serviceType());
            assertEquals(30000, event.odometerKm());
            assertEquals(1, event.partsReplaced().size());
            assertEquals("Oil Filter", event.partsReplaced().getFirst().partName());
            assertEquals("self_upload", event.source());
            assertEquals("Changed at local garage.", event.notes());
            assertEquals(List.of("s3://paymu-docs/uploads/receipt.pdf"), event.documentRefs());
        }

        @Test
        @DisplayName("Sets source to self_upload regardless of input")
        void alwaysSelfUpload() {
            SelfUploadRequest request = new SelfUploadRequest(
                    UUID.randomUUID().toString(), "2026-05-01", "brake_service",
                    20000, List.of(), null, null);

            OcrResult result = ocrStubService.process(request);
            assertEquals("self_upload", result.extractedEvent().source());
        }

        @Test
        @DisplayName("Generates a unique event_id")
        void generatesEventId() {
            SelfUploadRequest request = new SelfUploadRequest(
                    UUID.randomUUID().toString(), "2026-05-01", "inspection",
                    10000, List.of(), null, null);

            OcrResult result = ocrStubService.process(request);
            assertNotNull(result.extractedEvent().eventId());
            assertFalse(result.extractedEvent().eventId().isBlank());
        }

        @Test
        @DisplayName("Handles null parts_replaced gracefully")
        void nullParts() {
            SelfUploadRequest request = new SelfUploadRequest(
                    UUID.randomUUID().toString(), "2026-05-01", "oil_change",
                    15000, null, null, null);

            OcrResult result = ocrStubService.process(request);
            assertNotNull(result.extractedEvent().partsReplaced());
            assertTrue(result.extractedEvent().partsReplaced().isEmpty());
        }

        @Test
        @DisplayName("Handles null document_ref gracefully")
        void nullDocRef() {
            SelfUploadRequest request = new SelfUploadRequest(
                    UUID.randomUUID().toString(), "2026-05-01", "oil_change",
                    15000, List.of(), null, null);

            OcrResult result = ocrStubService.process(request);
            assertTrue(result.extractedEvent().documentRefs().isEmpty());
        }
    }

    // ── Maintenance Store ───────────────────────────────────────

    @Nested
    @DisplayName("MaintenanceStore")
    class StoreTests {

        private MaintenanceStore store;

        @BeforeEach
        void setUp() {
            store = new MaintenanceStore();
        }

        private MaintenanceEvent event(String vehicleId, String date, String type, int odometer) {
            return new MaintenanceEvent(
                    UUID.randomUUID().toString(), vehicleId, date, type,
                    odometer, List.of(), "oem_api", null, List.of());
        }

        @Test
        @DisplayName("Stores and retrieves events by vehicle")
        void storeAndRetrieve() {
            String vehicleId = UUID.randomUUID().toString();
            MaintenanceEvent e1 = event(vehicleId, "2026-01-15", "oil_change", 10000);
            MaintenanceEvent e2 = event(vehicleId, "2026-06-20", "brake_service", 15000);

            store.storeEvents(List.of(e1, e2));

            List<MaintenanceEvent> retrieved = store.getEventsByVehicle(vehicleId);
            assertEquals(2, retrieved.size());
        }

        @Test
        @DisplayName("Returns empty list for unknown vehicle")
        void unknownVehicle() {
            assertTrue(store.getEventsByVehicle("non-existent").isEmpty());
        }

        @Test
        @DisplayName("Timeline returns null for unknown vehicle")
        void timelineUnknown() {
            assertNull(store.getTimeline("non-existent"));
        }

        @Test
        @DisplayName("Timeline sorts events by service_date ascending")
        void timelineOrdering() {
            String vehicleId = UUID.randomUUID().toString();

            // Insert out of order
            store.storeEvents(List.of(
                    event(vehicleId, "2026-06-20", "brake_service", 15000),
                    event(vehicleId, "2026-01-15", "oil_change", 10000),
                    event(vehicleId, "2026-09-01", "tyre_rotation", 20000)));

            ServiceTimeline timeline = store.getTimeline(vehicleId);
            assertNotNull(timeline);
            assertEquals(3, timeline.totalServices());
            assertEquals(vehicleId, timeline.vehicleId());

            // Verify date ordering
            List<MaintenanceEvent> events = timeline.events();
            assertEquals("2026-01-15", events.get(0).serviceDate());
            assertEquals("2026-06-20", events.get(1).serviceDate());
            assertEquals("2026-09-01", events.get(2).serviceDate());
        }

        @Test
        @DisplayName("Timeline reports latest service date and odometer")
        void timelineLatest() {
            String vehicleId = UUID.randomUUID().toString();
            store.storeEvents(List.of(
                    event(vehicleId, "2026-01-15", "oil_change", 10000),
                    event(vehicleId, "2026-09-01", "full_service", 25000)));

            ServiceTimeline timeline = store.getTimeline(vehicleId);
            assertEquals("2026-09-01", timeline.latestServiceDate());
            assertEquals(25000, timeline.latestOdometerKm());
        }

        @Test
        @DisplayName("Isolates events across different vehicles")
        void vehicleIsolation() {
            String v1 = UUID.randomUUID().toString();
            String v2 = UUID.randomUUID().toString();

            store.storeEvents(List.of(
                    event(v1, "2026-01-15", "oil_change", 10000),
                    event(v2, "2026-03-20", "brake_service", 8000)));

            assertEquals(1, store.getEventsByVehicle(v1).size());
            assertEquals(1, store.getEventsByVehicle(v2).size());
            assertEquals("oil_change", store.getEventsByVehicle(v1).getFirst().serviceType());
            assertEquals("brake_service", store.getEventsByVehicle(v2).getFirst().serviceType());
        }

        @Test
        @DisplayName("Clear removes all data")
        void clear() {
            String vehicleId = UUID.randomUUID().toString();
            store.storeEvent(event(vehicleId, "2026-01-15", "oil_change", 10000));
            assertFalse(store.getEventsByVehicle(vehicleId).isEmpty());

            store.clear();
            assertTrue(store.getEventsByVehicle(vehicleId).isEmpty());
        }
    }
}
