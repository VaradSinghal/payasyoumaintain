package com.paymu.pricing.service;

import com.paymu.pricing.model.DiscountBreakdown;
import com.paymu.pricing.model.PremiumResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Core premium calculation logic for the Pay As You Maintain platform.
 *
 * <h3>Formula overview</h3>
 * <pre>
 *   base_premium   = IDV × depreciation_rate × base_od_rate
 *   od_premium     = base_premium × (1 - ncb_pct)
 *   dynamic_mult   = f(usage_score, maintenance_score)
 *   final_premium  = round(od_premium × dynamic_mult)
 * </pre>
 *
 * <h3>Dynamic multiplier derivation</h3>
 * <p>Composite score 100 → multiplier 0.70 (reward for best-in-class behaviour).
 * Composite score   0 → multiplier 1.50 (surcharge for worst-in-class).
 * Linear interpolation between the two anchors.</p>
 *
 * <h3>IDV &amp; depreciation table</h3>
 * <p>IDVs and vehicle age are hardcoded per the 5 demo vehicle IDs while a real
 * vehicle-registration lookup (Phase 4) is not yet wired up.</p>
 */
@Service
public class PricingEngine {

    // --- Constants -----------------------------------------------------------

    /** Base OD rate before any adjustments (IRDAI-style indicative rate). */
    private static final double BASE_OD_RATE = 0.026;       // 2.6 % of IDV

    /** Multiplier anchor for composite score = 100 (best). */
    private static final double MULT_AT_BEST = 0.70;

    /** Multiplier anchor for composite score = 0 (worst). */
    private static final double MULT_AT_WORST = 1.50;

    /** Score that defines the "neutral" zone — no uplift, no discount. */
    private static final double NEUTRAL_SCORE = 50.0;

    /** Neutral multiplier when no score data is available (cold-start). */
    private static final double NEUTRAL_MULTIPLIER = 1.0;

    // --- IRDAI depreciation slabs (vehicle age → rate applied against ex-showroom) ---
    /** Vehicle age (years) → depreciation rate to derive IDV */
    private static final Map<Integer, Double> DEPRECIATION_TABLE = Map.of(
            0, 0.95,   // < 6 months — 5 % depreciation
            1, 0.85,
            2, 0.80,
            3, 0.70,
            4, 0.60,
            5, 0.50
    );
    private static final double DEPRECIATION_BEYOND_5_YEARS = 0.40;

    // --- Demo vehicle catalogue (hardcoded; replaced by registration lookup in Phase 4) ---
    private record VehicleProfile(double exShowroomPrice, int vehicleAgeYears, double ncbPct,
                                   String label) {}

    private static final Map<String, VehicleProfile> VEHICLE_CATALOGUE = Map.of(
            "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    new VehicleProfile(1_200_000, 2, 0.20, "Perfect maintainer — mid-segment sedan, 2yr old"),
            "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                    new VehicleProfile(800_000, 4, 0.10, "Overdue — compact hatchback, 4yr old"),
            "c3d4e5f6-a7b8-9012-cdef-123456789012",
                    new VehicleProfile(1_500_000, 3, 0.00, "Recall vehicle — premium SUV, 3yr old, no NCB"),
            "d4e5f6a7-b8c9-0123-defa-234567890123",
                    new VehicleProfile(900_000, 5, 0.25, "Mixed sources — mid-range sedan, 5yr old"),
            "e5f6a7b8-c9d0-1234-efab-345678901234",
                    new VehicleProfile(1_800_000, 0, 0.00, "New vehicle — luxury SUV, <6 months old")
    );

    // --- Default profile for unknown vehicle IDs (cold-start / new enrolment) ---
    private static final VehicleProfile DEFAULT_PROFILE =
            new VehicleProfile(750_000, 3, 0.00, "Unknown vehicle — default profile");

    // -------------------------------------------------------------------------

    /**
     * Computes the premium for a vehicle given its latest risk score.
     *
     * @param vehicleId   UUID of the vehicle
     * @param requestId   idempotency key (echoed back)
     * @param policyId    policy UUID (echoed back)
     * @param usageScore       usage score 0–100, or null for cold-start (neutral)
     * @param maintenanceScore maintenance score 0–100, or null for cold-start (neutral)
     * @param compositeScore   composite score 0–100, or null for cold-start (neutral)
     * @return computed {@link PremiumResponse}
     */
    public PremiumResponse compute(String vehicleId, String requestId, String policyId,
                                   Double usageScore, Double maintenanceScore, Double compositeScore) {

        VehicleProfile profile = VEHICLE_CATALOGUE.getOrDefault(vehicleId, DEFAULT_PROFILE);

        // 1. IDV = ex-showroom × depreciation
        double depreciation = depreciation(profile.vehicleAgeYears());
        double idv = profile.exShowroomPrice() * depreciation;

        // 2. Base OD premium before NCB
        double basePremiumBeforeNcb = round2(idv * BASE_OD_RATE);

        // 3. NCB discount
        double ncbAdjPct = -profile.ncbPct() * 100;            // negative = discount
        double basePremiumAfterNcb = round2(basePremiumBeforeNcb * (1 - profile.ncbPct()));

        List<DiscountBreakdown> breakdown = new ArrayList<>();
        if (profile.ncbPct() > 0) {
            breakdown.add(new DiscountBreakdown("no_claim_bonus", ncbAdjPct));
        }

        // 4. Dynamic risk multiplier from composite score
        double effectiveComposite = compositeScore != null ? compositeScore : -1.0;
        double dynamicMultiplier;
        String scoreAdjReason;
        double scoreAdjPct;

        if (effectiveComposite < 0) {
            // Cold-start: no history → neutral multiplier
            dynamicMultiplier = NEUTRAL_MULTIPLIER;
            breakdown.add(new DiscountBreakdown("cold_start_neutral", 0.0));
        } else {
            // Linear interpolation: score 100 → 0.70, score 0 → 1.50
            dynamicMultiplier = round3(
                    MULT_AT_WORST + (MULT_AT_BEST - MULT_AT_WORST) * (effectiveComposite / 100.0));

            // Express as % adjustment relative to neutral (1.0)
            scoreAdjPct = round2((dynamicMultiplier - 1.0) * 100);
            scoreAdjReason = dynamicMultiplier < 1.0 ? "risk_score_discount" : "risk_score_surcharge";
            breakdown.add(new DiscountBreakdown(scoreAdjReason, scoreAdjPct));
        }

        // 5. Final premium
        double finalPremium = round2(basePremiumAfterNcb * dynamicMultiplier);

        Instant now = Instant.now();
        return new PremiumResponse(
                requestId,
                vehicleId,
                policyId,
                round2(basePremiumBeforeNcb),
                round3(dynamicMultiplier),
                finalPremium,
                "INR",
                breakdown,
                now.plus(30, ChronoUnit.DAYS).toString(),
                now.toString()
        );
    }

    // ---- Helpers ------------------------------------------------------------

    private double depreciation(int ageYears) {
        if (ageYears >= 5) return DEPRECIATION_BEYOND_5_YEARS;
        return DEPRECIATION_TABLE.getOrDefault(ageYears, DEPRECIATION_BEYOND_5_YEARS);
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
