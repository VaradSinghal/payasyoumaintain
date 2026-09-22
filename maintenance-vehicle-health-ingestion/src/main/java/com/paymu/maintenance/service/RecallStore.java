package com.paymu.maintenance.service;

import com.paymu.maintenance.model.RecallDetail;
import com.paymu.maintenance.model.RecallStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory stub for the OEM recall database.
 *
 * <p>Returns pre-seeded recall records for known vehicle IDs; all other
 * vehicles receive a clean "no open recall" response.  Real integration
 * with the OEM API is deferred to Phase 4.</p>
 *
 * <p>This service is the <em>only</em> source of recall truth in the
 * platform — free-text {@code notes} on maintenance events must not be
 * parsed for recall information.</p>
 */
@Service
public class RecallStore {

    /**
     * Pre-seeded recall entries keyed by vehicle ID.
     * Populated at startup by {@link SyntheticDataGenerator} and via
     * {@link #seed(String, RecallStatus)}.
     */
    private final Map<String, RecallStatus> recallsByVehicle = new ConcurrentHashMap<>();

    /**
     * Returns the recall status for a vehicle, or a default "no open recall"
     * record if the vehicle is not in the store.
     */
    public RecallStatus getRecallStatus(String vehicleId) {
        return recallsByVehicle.getOrDefault(vehicleId, noRecall(vehicleId));
    }

    /**
     * Seeds a recall status record (used during startup by the synthetic data
     * generator and in tests).
     */
    public void seed(String vehicleId, RecallStatus status) {
        recallsByVehicle.put(vehicleId, status);
    }

    /** Clears all stored data. Useful for tests. */
    public void clear() {
        recallsByVehicle.clear();
    }

    // ── Factory helpers ─────────────────────────────────────────

    public static RecallStatus noRecall(String vehicleId) {
        return new RecallStatus(
                vehicleId,
                false,
                0,
                List.of(),
                RecallStatus.SOURCE,
                Instant.now().toString());
    }

    public static RecallStatus withRecalls(String vehicleId, List<RecallDetail> details) {
        return new RecallStatus(
                vehicleId,
                true,
                details.size(),
                details,
                RecallStatus.SOURCE,
                Instant.now().toString());
    }
}
