package com.paymu.pricing.service;

import com.paymu.pricing.model.DiscountBreakdown;
import com.paymu.pricing.model.PremiumResponse;
import com.paymu.pricing.model.ScoreResponse;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link PricingEngine} — purely in-process, no HTTP.
 */
@DisplayName("PricingEngine")
class PricingEngineTest {

    private PricingEngine engine;

    // Known demo vehicle IDs
    private static final String PERFECT_MAINTAINER = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String RECALL_VEHICLE     = "c3d4e5f6-a7b8-9012-cdef-123456789012";
    private static final String UNKNOWN_VEHICLE    = "ffffffff-0000-0000-0000-000000000000";

    @BeforeEach
    void setUp() {
        engine = new PricingEngine();
    }

    // ── Cold-start: no score data ─────────────────────────────────────────────

    @Test
    @DisplayName("Cold-start (no score) → neutral multiplier 1.0, no error")
    void coldStart_noScore_neutralMultiplier() {
        PremiumResponse resp = engine.compute(UNKNOWN_VEHICLE, rid(), pid(), null);

        assertEquals(1.0, resp.dynamicMultiplier(), 0.001,
                "Cold-start must use neutral multiplier 1.0");
        assertTrue(resp.finalPremium() > 0, "Final premium must be positive even on cold-start");

        boolean hasColdStart = resp.discountBreakdown().stream()
                .anyMatch(d -> d.reason().equals("cold_start_neutral"));
        assertTrue(hasColdStart, "discount_breakdown must contain 'cold_start_neutral' entry");
    }

    @Test
    @DisplayName("Cold-start finalPremium equals basePremium × (1 - NCB) × 1.0")
    void coldStart_finalPremium_equalsNcbAdjustedBase() {
        // Unknown vehicle → DEFAULT_PROFILE: ₹7.5L, 3yr old, 0% NCB
        // IDV = 750000 × 0.70 = 525000, basePremium = 525000 × 0.026 = 13650
        // No NCB, multiplier = 1.0 → finalPremium = 13650.00
        PremiumResponse resp = engine.compute(UNKNOWN_VEHICLE, rid(), pid(), null);
        assertEquals(13650.00, resp.finalPremium(), 1.0,
                "Default profile cold-start premium should be ~₹13,650");
    }

    // ── Score-driven multiplier ───────────────────────────────────────────────

    @Test
    @DisplayName("Composite score 100 → multiplier 0.70 (maximum discount)")
    void perfectScore_multiplierAtBest() {
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), score(100.0, 100.0, 100.0));

        assertEquals(0.700, resp.dynamicMultiplier(), 0.001,
                "Score 100 must map to multiplier 0.70");
    }

    @Test
    @DisplayName("Composite score 0 → multiplier 1.50 (maximum surcharge)")
    void worstScore_multiplierAtWorst() {
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), score(0.0, 0.0, 0.0));

        assertEquals(1.500, resp.dynamicMultiplier(), 0.001,
                "Score 0 must map to multiplier 1.50");
    }

    @Test
    @DisplayName("Composite score 50 → multiplier ~1.10 (midpoint)")
    void midpointScore_interpolatedMultiplier() {
        // At score 50: 1.50 + (0.70 - 1.50) × (50/100) = 1.50 - 0.40 = 1.10
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), score(50.0, 50.0, 50.0));

        assertEquals(1.10, resp.dynamicMultiplier(), 0.001,
                "Score 50 must interpolate to multiplier ~1.10");
    }

    // ── Core requirement: same vehicle, different maintenance score → different premiums ──

    @Test
    @DisplayName("Same vehicle at two maintenance scores produces two different final premiums")
    void differentMaintenanceScores_produceDifferentPremiums() {
        // Perfect maintenance (score 100) vs. worst maintenance (score 0), usage held constant
        PremiumResponse good = engine.compute(
                RECALL_VEHICLE, rid(), pid(),
                score(80.0, 100.0, (80.0 * 0.6) + (100.0 * 0.4))
        );

        PremiumResponse bad = engine.compute(
                RECALL_VEHICLE, rid(), pid(),
                score(80.0, 30.0, (80.0 * 0.6) + (30.0 * 0.4))
        );

        assertTrue(bad.finalPremium() > good.finalPremium(),
                "Lower maintenance score must produce a higher final premium. " +
                "good=₹" + good.finalPremium() + " bad=₹" + bad.finalPremium());
        assertTrue(bad.dynamicMultiplier() > good.dynamicMultiplier(),
                "Lower maintenance score must produce a higher dynamic multiplier");
    }

    // ── NCB application ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Vehicle with NCB 20% has lower premium than same vehicle without NCB")
    void ncb_reducesBasePremium() {
        // PERFECT_MAINTAINER has 20% NCB; RECALL_VEHICLE has 0% NCB.
        // Both at neutral composite 50 to isolate the NCB effect.
        PremiumResponse withNcb    = engine.compute(PERFECT_MAINTAINER, rid(), pid(), score(50.0, 50.0, 50.0));
        PremiumResponse withoutNcb = engine.compute(RECALL_VEHICLE,     rid(), pid(), score(50.0, 50.0, 50.0));

        // Must be cheaper even though PERFECT_MAINTAINER has a higher IDV
        boolean ncbBreakdownPresent = withNcb.discountBreakdown().stream()
                .anyMatch(d -> d.reason().equals("no_claim_bonus") && d.adjustmentPct() < 0);
        assertTrue(ncbBreakdownPresent, "NCB vehicle must have a negative adjustment in discount_breakdown");
    }

    // ── Response shape ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Response fields are fully populated per premium-response.schema.json")
    void responseShape_allRequiredFieldsPopulated() {
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), score(80.0, 90.0, 86.0));

        assertNotNull(resp.requestId());
        assertNotNull(resp.vehicleId());
        assertNotNull(resp.policyId());
        assertTrue(resp.basePremium() > 0);
        assertTrue(resp.dynamicMultiplier() > 0);
        assertTrue(resp.finalPremium() > 0);
        assertEquals("INR", resp.currency());
        assertNotNull(resp.computedAt());
        assertNotNull(resp.validUntil());
        assertFalse(resp.discountBreakdown().isEmpty(),
                "discount_breakdown must contain at least one entry");
        assertNotNull(resp.scoreDetail());
        assertEquals(80.0, resp.scoreDetail().usageScore());
        assertEquals(90.0, resp.scoreDetail().maintenanceScore());
        assertEquals(86.0, resp.scoreDetail().compositeScore());
        assertEquals("scored", resp.scoreDetail().healthStatus());
        assertEquals("Dynamic Risk Multiplier is illustrative, pending actuarial validation on real claims data (Phase A).", resp.methodologyNote());
    }

    @Test
    @DisplayName("Null usage_score, null composite_score, and usage_data_unavailable flag are passed to score_detail with cold_start_neutral multiplier")
    void nullUsageScore_passesThroughWithColdStartNeutral() {
        ScoreResponse scoreWithNullUsage = new ScoreResponse(
                "s-null", "v-123",
                null,
                100.0,
                null,
                "2026-10-11T10:00:00Z", "v1.2.0",
                "scored", 100.0, "verified", "renew", false,
                java.util.List.of("usage_data_unavailable"),
                java.util.List.of()
        );

        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), scoreWithNullUsage);

        assertEquals(1.0, resp.dynamicMultiplier());
        assertTrue(resp.discountBreakdown().stream().anyMatch(d -> d.reason().equals("cold_start_neutral") && d.adjustmentPct() == 0.0));
        assertNotNull(resp.scoreDetail());
        assertNull(resp.scoreDetail().usageScore());
        assertEquals(100.0, resp.scoreDetail().maintenanceScore());
        assertNull(resp.scoreDetail().compositeScore());
        assertTrue(resp.scoreDetail().dataFlags().contains("usage_data_unavailable"));
    }

    // ── Depreciation slab tests ───────────────────────────────────────────────

    @Test
    @DisplayName("Depreciation slab covers every age from 0 to 6 correctly")
    void depreciation_allAgesFrom0To6() {
        assertEquals(0.95, engine.depreciation(0), 0.001, "Age 0 -> 0.95");
        assertEquals(0.85, engine.depreciation(1), 0.001, "Age 1 -> 0.85");
        assertEquals(0.80, engine.depreciation(2), 0.001, "Age 2 -> 0.80");
        assertEquals(0.70, engine.depreciation(3), 0.001, "Age 3 -> 0.70");
        assertEquals(0.60, engine.depreciation(4), 0.001, "Age 4 -> 0.60");
        assertEquals(0.50, engine.depreciation(5), 0.001, "Age 5 -> 0.50");
        assertEquals(0.40, engine.depreciation(6), 0.001, "Age 6 (>5) -> 0.40");
        assertEquals(0.40, engine.depreciation(10), 0.001, "Age 10 (>5) -> 0.40");
    }

    @Test
    @DisplayName("Vehicle d4e5... (age 5, exShowroom 900,000) base_premium is 11,700 (was 9,360)")
    void d4e5_vehicleBasePremium_is11700() {
        String d4e5VehicleId = "d4e5f6a7-b8c9-0123-defa-234567890123";
        // Compute quote on cold-start (null score)
        PremiumResponse resp = engine.compute(d4e5VehicleId, rid(), pid(), null);

        // IDV = 900,000 * 0.50 = 450,000; Base premium = 450,000 * 0.026 = 11,700.00
        assertEquals(11700.00, resp.basePremium(), 0.01, "Base premium for d4e5... must be 11,700.00");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String rid() { return java.util.UUID.randomUUID().toString(); }
    private static String pid() { return java.util.UUID.randomUUID().toString(); }

    private ScoreResponse score(Double usage, Double maintenance, Double composite) {
        return new ScoreResponse("s-123", "v-123",
                usage,
                maintenance,
                composite,
                "2026-09-25T10:00:00Z", "v1", 
                "scored", maintenance, "verified", "renew", false, java.util.List.of(),
                java.util.List.of());
    }
}
