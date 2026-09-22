package com.paymu.maintenance.service;

import com.paymu.maintenance.model.MaintenanceEvent;
import com.paymu.maintenance.model.PartReplaced;
import com.paymu.maintenance.model.RecallDetail;
import com.paymu.maintenance.model.RecallStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates synthetic maintenance histories for five sample vehicles.
 *
 * <h3>Vehicle profiles</h3>
 * <ol>
 *   <li><b>Perfect maintainer</b> — all services on schedule, OEM API sourced</li>
 *   <li><b>Overdue</b> — was regular, then stopped servicing 8 months ago</li>
 *   <li><b>Missed interval + open recall</b> — skipped a brake service, has an
 *       inspection noting an outstanding battery recall</li>
 *   <li><b>Mixed sources</b> — some OEM API, some self-upload, inconsistent intervals</li>
 *   <li><b>New vehicle</b> — just purchased, only initial inspection + one oil change</li>
 * </ol>
 */
@Service
public class SyntheticDataGenerator {

    private final RecallStore recallStore;

    public SyntheticDataGenerator(RecallStore recallStore) {
        this.recallStore = recallStore;
    }
    static final String[] VEHICLE_IDS = {
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",   // Perfect maintainer
            "b2c3d4e5-f6a7-8901-bcde-f12345678901",   // Overdue
            "c3d4e5f6-a7b8-9012-cdef-123456789012",   // Missed interval + recall
            "d4e5f6a7-b8c9-0123-defa-234567890123",   // Mixed sources
            "e5f6a7b8-c9d0-1234-efab-345678901234"    // New vehicle
    };

    /**
     * Generates all synthetic maintenance events and seeds recall status.
     */
    public List<MaintenanceEvent> generate() {
        LocalDate today = LocalDate.now();
        List<MaintenanceEvent> all = new ArrayList<>();

        all.addAll(generatePerfectMaintainer(today));
        all.addAll(generateOverdue(today));
        all.addAll(generateMissedWithRecall(today));
        all.addAll(generateMixedSources(today));
        all.addAll(generateNewVehicle(today));

        seedRecallData();

        return all;
    }

    /**
     * Seeds the {@link RecallStore} with one real recall for vehicle 3
     * (missed-interval profile) and clean no-recall records for the rest.
     * This is the single source of recall truth — maintenance notes no longer
     * carry recall information.
     */
    private void seedRecallData() {
        // Vehicle 0: Perfect maintainer — no recalls
        recallStore.seed(VEHICLE_IDS[0], RecallStore.noRecall(VEHICLE_IDS[0]));

        // Vehicle 1: Overdue — no recalls
        recallStore.seed(VEHICLE_IDS[1], RecallStore.noRecall(VEHICLE_IDS[1]));

        // Vehicle 2: Missed interval — one open recall (battery management)
        recallStore.seed(VEHICLE_IDS[2], RecallStore.withRecalls(VEHICLE_IDS[2], List.of(
                new RecallDetail(
                        "RC-2026-0042",
                        "Battery management module may fail to isolate the high-voltage pack " +
                        "during a collision. Risk of thermal runaway.",
                        "2026-03-15"))));

        // Vehicle 3: Mixed sources — no recalls
        recallStore.seed(VEHICLE_IDS[3], RecallStore.noRecall(VEHICLE_IDS[3]));

        // Vehicle 4: New vehicle — no recalls
        recallStore.seed(VEHICLE_IDS[4], RecallStore.noRecall(VEHICLE_IDS[4]));
    }
    // ── Vehicle 1: Perfect maintainer ──────────────────────────

    private List<MaintenanceEvent> generatePerfectMaintainer(LocalDate today) {
        String v = VEHICLE_IDS[0];
        List<MaintenanceEvent> events = new ArrayList<>();

        // Regular oil changes every ~5 months
        events.add(event(v, today.minusMonths(20), "oil_change", 15000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Scheduled oil change — on time."));

        events.add(event(v, today.minusMonths(15), "tyre_rotation", 20000, "oem_api",
                List.of(), "Tyre rotation per OEM schedule."));

        events.add(event(v, today.minusMonths(10), "oil_change", 25000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Scheduled oil change — on time."));

        events.add(event(v, today.minusMonths(8), "brake_service", 28000, "oem_api",
                List.of(part("Front Brake Pads", "BP-FR-2024", 1)),
                "Front brake pads replaced. Rear pads at 60% life."));

        events.add(event(v, today.minusMonths(5), "oil_change", 33000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Scheduled oil change — on time."));

        events.add(event(v, today.minusMonths(2), "full_service", 38000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1),
                        part("Air Filter", "AF-2024A", 1),
                        part("Cabin Filter", "CF-2024A", 1)),
                "Annual full service. All systems nominal."));

        return events;
    }

    // ── Vehicle 2: Overdue ─────────────────────────────────────

    private List<MaintenanceEvent> generateOverdue(LocalDate today) {
        String v = VEHICLE_IDS[1];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(event(v, today.minusMonths(18), "oil_change", 22000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Regular oil change."));

        events.add(event(v, today.minusMonths(14), "tyre_rotation", 27000, "oem_api",
                List.of(), "Tyre rotation."));

        events.add(event(v, today.minusMonths(12), "oil_change", 32000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Last regular oil change."));

        // Nothing since — 12 months ago, severely overdue

        return events;
    }

    // ── Vehicle 3: Missed interval + open recall ───────────────

    private List<MaintenanceEvent> generateMissedWithRecall(LocalDate today) {
        String v = VEHICLE_IDS[2];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(event(v, today.minusMonths(16), "oil_change", 18000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Regular oil change."));

        events.add(event(v, today.minusMonths(11), "oil_change", 24000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Regular oil change."));

        // MISSED: brake_service was due at ~28,000 km but never done

        events.add(event(v, today.minusMonths(6), "oil_change", 31000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Oil change done. NOTE: Brake service overdue — last inspection flagged worn pads."));

        // Inspection noting brake service urgency; recall status is modeled separately in RecallStore
        events.add(event(v, today.minusMonths(3), "inspection", 34000, "inspection",
                List.of(),
                "Annual inspection. Brake pads at 15% life — brake service critically overdue. "
                        + "Customer advised to schedule immediately. See recall status endpoint for open recalls."));

        return events;
    }

    // ── Vehicle 4: Mixed sources ───────────────────────────────

    private List<MaintenanceEvent> generateMixedSources(LocalDate today) {
        String v = VEHICLE_IDS[3];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(event(v, today.minusMonths(14), "oil_change", 12000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Dealership service."));

        // Self-uploaded — went to a local garage
        events.add(selfUploadEvent(v, today.minusMonths(10), "brake_service", 18000,
                List.of(part("Front Brake Pads", "Generic", 1)),
                "Local garage. Receipt uploaded.",
                "s3://paymu-docs/uploads/" + UUID.randomUUID() + ".pdf"));

        events.add(event(v, today.minusMonths(7), "oil_change", 24000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Back at dealership."));

        // Self-uploaded tyre rotation — late by ~2 months
        events.add(selfUploadEvent(v, today.minusMonths(3), "tyre_rotation", 31000,
                List.of(),
                "Tyre rotation at neighbourhood shop. Was due at 28k km.",
                "s3://paymu-docs/uploads/" + UUID.randomUUID() + ".jpg"));

        events.add(event(v, today.minusMonths(1), "oil_change", 34000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Scheduled oil change — on time."));

        return events;
    }

    // ── Vehicle 5: New vehicle ─────────────────────────────────

    private List<MaintenanceEvent> generateNewVehicle(LocalDate today) {
        String v = VEHICLE_IDS[4];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(event(v, today.minusMonths(2), "inspection", 500, "oem_api",
                List.of(),
                "Pre-delivery inspection. All systems OK."));

        events.add(event(v, today.minusWeeks(2), "oil_change", 5000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "First scheduled oil change at 5000 km."));

        return events;
    }

    // ── Helpers ─────────────────────────────────────────────────

    private MaintenanceEvent event(String vehicleId, LocalDate date, String serviceType,
                                    int odometerKm, String source,
                                    List<PartReplaced> parts, String notes) {
        return new MaintenanceEvent(
                UUID.randomUUID().toString(),
                vehicleId,
                date.toString(),
                serviceType,
                odometerKm,
                parts,
                source,
                notes,
                List.of());
    }

    private MaintenanceEvent selfUploadEvent(String vehicleId, LocalDate date, String serviceType,
                                              int odometerKm, List<PartReplaced> parts,
                                              String notes, String docRef) {
        return new MaintenanceEvent(
                UUID.randomUUID().toString(),
                vehicleId,
                date.toString(),
                serviceType,
                odometerKm,
                parts,
                "self_upload",
                notes,
                List.of(docRef));
    }

    private PartReplaced part(String name, String number, int qty) {
        return new PartReplaced(name, number, qty);
    }
}
