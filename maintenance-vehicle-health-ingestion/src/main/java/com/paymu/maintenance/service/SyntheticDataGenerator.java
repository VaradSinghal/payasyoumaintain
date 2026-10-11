package com.paymu.maintenance.service;

import com.paymu.maintenance.model.MaintenanceEvent;
import com.paymu.maintenance.model.PartReplaced;
import com.paymu.maintenance.model.RecallDetail;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Generates synthetic maintenance histories for five sample vehicles telling distinct stories.
 *
 * <h3>Vehicle profiles</h3>
 * <ol>
 *   <li><b>NORMAL</b> (a1b2c3d4...): oem_api full_service ~45 days ago plus brake_service within 24m, no recall.</li>
 *   <li><b>POOR</b> (b2c3d4e5...): oem_api, last service ~800 days ago, no brake service in 24m, OPEN RECALL RC-2026-0042.</li>
 *   <li><b>CONFLICTING</b> (c3d4e5f6...): oem_api full_service 100 days ago at 42k km AND self_upload 95 days ago at 38.5k km.</li>
 *   <li><b>UNVERIFIED</b> (d4e5f6a7...): only self_upload records (oil change + brake service ~10m ago).</li>
 *   <li><b>INSUFFICIENT</b> (e5f6a7b8...): ZERO service events.</li>
 * </ol>
 */
@Service
public class SyntheticDataGenerator {

    private final RecallStore recallStore;

    public SyntheticDataGenerator(RecallStore recallStore) {
        this.recallStore = recallStore;
    }

    public static final String[] VEHICLE_IDS = {
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",   // NORMAL
            "b2c3d4e5-f6a7-8901-bcde-f12345678901",   // POOR
            "c3d4e5f6-a7b8-9012-cdef-123456789012",   // CONFLICTING
            "d4e5f6a7-b8c9-0123-defa-234567890123",   // UNVERIFIED
            "e5f6a7b8-c9d0-1234-efab-345678901234"    // INSUFFICIENT
    };

    /**
     * Generates all synthetic maintenance events and seeds recall status.
     */
    public List<MaintenanceEvent> generate() {
        LocalDate today = LocalDate.now();
        List<MaintenanceEvent> all = new ArrayList<>();

        all.addAll(generateNormal(today));
        all.addAll(generatePoor(today));
        all.addAll(generateConflicting(today));
        all.addAll(generateUnverified(today));
        all.addAll(generateInsufficient(today));

        seedRecallData(today);

        return all;
    }

    /**
     * Seeds the {@link RecallStore} with RC-2026-0042 on vehicle 1 (POOR) and clean no-recall for all others.
     */
    private void seedRecallData(LocalDate today) {
        // Vehicle 0: NORMAL — no recalls
        recallStore.seed(VEHICLE_IDS[0], RecallStore.noRecall(VEHICLE_IDS[0]));

        // Vehicle 1: POOR — one open recall (battery management)
        recallStore.seed(VEHICLE_IDS[1], RecallStore.withRecalls(VEHICLE_IDS[1], List.of(
                new RecallDetail(
                        "RC-2026-0042",
                        "Battery management module may fail to isolate the high-voltage pack during a collision. Risk of thermal runaway.",
                        today.minusMonths(6).toString()))));

        // Vehicle 2: CONFLICTING — no recalls
        recallStore.seed(VEHICLE_IDS[2], RecallStore.noRecall(VEHICLE_IDS[2]));

        // Vehicle 3: UNVERIFIED — no recalls
        recallStore.seed(VEHICLE_IDS[3], RecallStore.noRecall(VEHICLE_IDS[3]));

        // Vehicle 4: INSUFFICIENT — no recalls
        recallStore.seed(VEHICLE_IDS[4], RecallStore.noRecall(VEHICLE_IDS[4]));
    }

    // ── Vehicle 0: NORMAL ──────────────────────────────────────
    private List<MaintenanceEvent> generateNormal(LocalDate today) {
        String v = VEHICLE_IDS[0];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(event(v, today.minusMonths(8), "brake_service", 28000, "oem_api",
                List.of(part("Front Brake Pads", "BP-FR-2024", 1)),
                "Front brake pads replaced. OEM verified."));

        events.add(event(v, today.minusDays(45), "full_service", 35000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1),
                        part("Air Filter", "AF-2024A", 1)),
                "Full service. All systems nominal."));

        return events;
    }

    // ── Vehicle 1: POOR ────────────────────────────────────────
    private List<MaintenanceEvent> generatePoor(LocalDate today) {
        String v = VEHICLE_IDS[1];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(event(v, today.minusDays(800), "oil_change", 20000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Last recorded service 800 days ago."));

        return events;
    }

    // ── Vehicle 2: CONFLICTING ─────────────────────────────────
    private List<MaintenanceEvent> generateConflicting(LocalDate today) {
        String v = VEHICLE_IDS[2];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(event(v, today.minusDays(100), "full_service", 42000, "oem_api",
                List.of(part("Engine Oil 5W-30", "OIL-5W30-5L", 1),
                        part("Oil Filter", "OF-2024A", 1)),
                "Annual full service at certified dealer."));

        events.add(selfUploadEvent(v, today.minusDays(95), "oil_change", 38500,
                List.of(part("Generic Oil 5W-30", "GEN-5W30", 1)),
                "Receipt from local garage.",
                "s3://paymu-docs/uploads/" + UUID.randomUUID() + ".pdf"));

        return events;
    }

    // ── Vehicle 3: UNVERIFIED ───────────────────────────────────
    private List<MaintenanceEvent> generateUnverified(LocalDate today) {
        String v = VEHICLE_IDS[3];
        List<MaintenanceEvent> events = new ArrayList<>();

        events.add(selfUploadEvent(v, today.minusMonths(10), "brake_service", 25000,
                List.of(part("Front Brake Pads", "BP-GENERIC", 1)),
                "Local garage brake service. Receipt uploaded.",
                "s3://paymu-docs/uploads/" + UUID.randomUUID() + ".pdf"));

        events.add(selfUploadEvent(v, today.minusDays(25), "oil_change", 30000,
                List.of(part("Synthetic Oil 5W-30", "OIL-5W30-5L", 1)),
                "Neighbourhood shop oil change receipt.",
                "s3://paymu-docs/uploads/" + UUID.randomUUID() + ".pdf"));

        return events;
    }

    // ── Vehicle 4: INSUFFICIENT ─────────────────────────────────
    private List<MaintenanceEvent> generateInsufficient(LocalDate today) {
        return List.of();
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
