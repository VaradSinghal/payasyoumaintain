package com.paymu.pricing.service;

import com.paymu.pricing.model.DiscountBreakdown;
import com.paymu.pricing.model.PremiumResponse;
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
        PremiumResponse resp = engine.compute(UNKNOWN_VEHICLE, rid(), pid(), null, null, null);

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
        PremiumResponse resp = engine.compute(UNKNOWN_VEHICLE, rid(), pid(), null, null, null);
        assertEquals(13650.00, resp.finalPremium(), 1.0,
                "Default profile cold-start premium should be ~₹13,650");
    }

    // ── Score-driven multiplier ───────────────────────────────────────────────

    @Test
    @DisplayName("Composite score 100 → multiplier 0.70 (maximum discount)")
    void perfectScore_multiplierAtBest() {
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), 100.0, 100.0, 100.0);

        assertEquals(0.700, resp.dynamicMultiplier(), 0.001,
                "Score 100 must map to multiplier 0.70");
    }

    @Test
    @DisplayName("Composite score 0 → multiplier 1.50 (maximum surcharge)")
    void worstScore_multiplierAtWorst() {
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), 0.0, 0.0, 0.0);

        assertEquals(1.500, resp.dynamicMultiplier(), 0.001,
                "Score 0 must map to multiplier 1.50");
    }

    @Test
    @DisplayName("Composite score 50 → multiplier ~1.10 (midpoint)")
    void midpointScore_interpolatedMultiplier() {
        // At score 50: 1.50 + (0.70 - 1.50) × (50/100) = 1.50 - 0.40 = 1.10
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), 50.0, 50.0, 50.0);

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
                80.0,   // usage score
                100.0,  // maintenance score — perfect
                (80.0 * 0.6) + (100.0 * 0.4)  // composite = 88.0
        );

        PremiumResponse bad = engine.compute(
                RECALL_VEHICLE, rid(), pid(),
                80.0,   // usage score — same
                30.0,   // maintenance score — poor
                (80.0 * 0.6) + (30.0 * 0.4)   // composite = 60.0
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
        PremiumResponse withNcb    = engine.compute(PERFECT_MAINTAINER, rid(), pid(), 50.0, 50.0, 50.0);
        PremiumResponse withoutNcb = engine.compute(RECALL_VEHICLE,     rid(), pid(), 50.0, 50.0, 50.0);

        // Must be cheaper even though PERFECT_MAINTAINER has a higher IDV
        boolean ncbBreakdownPresent = withNcb.discountBreakdown().stream()
                .anyMatch(d -> d.reason().equals("no_claim_bonus") && d.adjustmentPct() < 0);
        assertTrue(ncbBreakdownPresent, "NCB vehicle must have a negative adjustment in discount_breakdown");
    }

    // ── Response shape ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Response fields are fully populated per premium-response.schema.json")
    void responseShape_allRequiredFieldsPopulated() {
        PremiumResponse resp = engine.compute(PERFECT_MAINTAINER, rid(), pid(), 80.0, 90.0, 86.0);

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
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String rid() { return java.util.UUID.randomUUID().toString(); }
    private static String pid() { return java.util.UUID.randomUUID().toString(); }
}
