package com.paymu.claims.service;

import com.paymu.claims.model.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists FNOL claims in memory.
 *
 * <p>In Phase 4 this will be backed by PostgreSQL. The {@link #clear()} method
 * exists for test isolation.</p>
 */
@Service
public class ClaimStore {

    private final Map<String, FnolResponse> claims = new ConcurrentHashMap<>();

    public FnolResponse save(FnolRequest request, TriageService.TriageResult triage) {
        FnolResponse response = new FnolResponse(
                UUID.randomUUID().toString(),
                request.vehicleId(),
                request.policyId(),
                request.claimedCause(),
                triage.decision(),
                triage.reasons(),
                triage.maintenanceSignal(),
                Instant.now().toString()
        );
        claims.put(response.claimId(), response);
        return response;
    }

    public FnolResponse findById(String claimId) {
        return claims.get(claimId);
    }

    public void clear() {
        claims.clear();
    }
}
