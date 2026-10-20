package com.paymu.pricing.service;

import com.paymu.pricing.config.PricingProperties;
import com.paymu.pricing.exception.ServiceUnavailableException;
import com.paymu.pricing.model.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Core premium calculation logic for the Pay As You Maintain platform.
 *
 * <h3>Formula overview</h3>
 * <pre>
 *   idv            = ex_showroom_price × depreciation_rate(age)
 *   base_premium   = round2(idv × base_od_rate)
 *   od_premium     = round2(base_premium × (1 - ncb_pct))
 *   dynamic_mult   = f(usage_score, maintenance_score)
 *   final_premium  = round2(od_premium × dynamic_mult)
 * </pre>
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

    /** Neutral multiplier when no score data is available (cold-start). */
    private static final double NEUTRAL_MULTIPLIER = 1.0;

    /** Fixed generic default profile price (INR 7,50,000). */
    private static final long GENERIC_DEFAULT_PRICE_INR = 750_000L;

    /** Fixed generic default profile age in years. */
    private static final int GENERIC_DEFAULT_AGE_YEARS = 3;

    // --- IRDAI depreciation slabs ---
    private static final Map<Integer, Double> DEPRECIATION_TABLE = Map.of(
            0, 0.95,
            1, 0.85,
            2, 0.80,
            3, 0.70,
            4, 0.60,
            5, 0.50
    );
    private static final double DEPRECIATION_BEYOND_5_YEARS = 0.40;

    private final VehicleCatalogueService catalogueService;
    private final PricingProperties pricingProperties;
    private final Clock clock;

    public PricingEngine(VehicleCatalogueService catalogueService,
                         PricingProperties pricingProperties,
                         Clock clock) {
        this.catalogueService = catalogueService;
        this.pricingProperties = pricingProperties;
        this.clock = clock;
    }

    /**
     * Derives the actuarial pricing basis according to the four outcomes:
     * <ul>
     *   <li>(a) Registered vehicle with known segment: source "registered" (or "demo_profile" for the 5 demo IDs)</li>
     *   <li>(b) Registered vehicle with missing/unknown segment: source "generic_default" at 750,000 with REAL age</li>
     *   <li>(c) Identity returned 404 (unregistered): fixed generic profile (750,000, age 3), source "generic_default"</li>
     *   <li>(d) Identity unreachable / 5xx with no cache: throws {@link ServiceUnavailableException}</li>
     * </ul>
     */
    public PricingBasis derivePricingBasis(String vehicleId, ProfileLookupResult profileLookup) {
        if (profileLookup == null || profileLookup.isUnavailable()) {
            throw new ServiceUnavailableException(
                    "Identity service is unavailable and no cached profile exists for vehicle " + vehicleId
            );
        }

        if (profileLookup.isUnregistered()) {
            // Outcome (c): identity answers 404 (unregistered)
            double depFactor = depreciation(GENERIC_DEFAULT_AGE_YEARS);
            double idv = GENERIC_DEFAULT_PRICE_INR * depFactor;
            return new PricingBasis(
                    "generic_default",
                    null,
                    GENERIC_DEFAULT_PRICE_INR,
                    GENERIC_DEFAULT_AGE_YEARS,
                    depFactor,
                    idv,
                    0.0
            );
        }

        // Profile found (either from live identity or in-memory cache)
        VehicleProfileResponse profile = profileLookup.profile();
        LocalDate today = LocalDate.now(clock);
        LocalDate regDate = profile.registrationDate();
        int ageYears = (regDate != null) ? Math.max(0, Period.between(regDate, today).getYears()) : GENERIC_DEFAULT_AGE_YEARS;
        double depFactor = depreciation(ageYears);

        String segmentId = profile.vehicleSegmentId();
        Optional<VehicleCatalogueEntry> catalogueEntry = catalogueService.findById(segmentId);

        if (catalogueEntry.isPresent()) {
            // Outcome (a): vehicle found with a known segment
            long exShowroom = catalogueEntry.get().exShowroomPriceInr();
            double idv = exShowroom * depFactor;
            boolean isDemo = pricingProperties.getDemoNcb().containsKey(vehicleId);
            String source = isDemo ? "demo_profile" : "registered";
            double ncbPct = isDemo ? pricingProperties.getDemoNcb().get(vehicleId) : 0.0;

            return new PricingBasis(
                    source,
                    segmentId,
                    exShowroom,
                    ageYears,
                    depFactor,
                    idv,
                    ncbPct
            );
        } else {
            // Outcome (b): found but segment missing or unknown -> generic_default at 750,000 with REAL age
            long exShowroom = GENERIC_DEFAULT_PRICE_INR;
            double idv = exShowroom * depFactor;
            double ncbPct = pricingProperties.getDemoNcb().getOrDefault(vehicleId, 0.0);

            return new PricingBasis(
                    "generic_default",
                    segmentId,
                    exShowroom,
                    ageYears,
                    depFactor,
                    idv,
                    ncbPct
            );
        }
    }

    /**
     * Computes the vehicle age in months from the registration date for scorer input.
     */
    public int calculateAgeMonths(LocalDate registrationDate) {
        if (registrationDate == null) {
            return GENERIC_DEFAULT_AGE_YEARS * 12;
        }
        LocalDate today = LocalDate.now(clock);
        int months = (int) Period.between(registrationDate, today).toTotalMonths();
        return Math.max(0, months);
    }

    /**
     * Computes the personalised dynamic premium quote for a vehicle.
     */
    public PremiumResponse compute(
            String vehicleId,
            String requestId,
            String policyId,
            PricingBasis basis,
            ScoreResponse score) {

        // 1. IDV from pricing basis
        double idv = basis.idv();

        // 2. Base OD premium before NCB
        double basePremiumBeforeNcb = round2(idv * BASE_OD_RATE);

        // 3. NCB discount
        double ncbPct = basis.ncbPct();
        double ncbAdjPct = -ncbPct * 100;
        double basePremiumAfterNcb = round2(basePremiumBeforeNcb * (1 - ncbPct));

        List<DiscountBreakdown> breakdown = new ArrayList<>();
        if (ncbPct > 0) {
            breakdown.add(new DiscountBreakdown("no_claim_bonus", ncbAdjPct));
        }

        // 4. Dynamic risk multiplier from composite score
        double effectiveComposite = (score != null && score.compositeScore() != null) ? score.compositeScore() : -1.0;
        double dynamicMultiplier;
        String scoreAdjReason;
        double scoreAdjPct;

        if (effectiveComposite < 0) {
            dynamicMultiplier = NEUTRAL_MULTIPLIER;
            breakdown.add(new DiscountBreakdown("cold_start_neutral", 0.0));
        } else {
            dynamicMultiplier = round3(
                    MULT_AT_WORST + (MULT_AT_BEST - MULT_AT_WORST) * (effectiveComposite / 100.0)
            );
            scoreAdjPct = round2((dynamicMultiplier - 1.0) * 100);
            scoreAdjReason = dynamicMultiplier < 1.0 ? "risk_score_discount" : "risk_score_surcharge";
            breakdown.add(new DiscountBreakdown(scoreAdjReason, scoreAdjPct));
        }

        // 5. Final premium
        double finalPremium = round2(basePremiumAfterNcb * dynamicMultiplier);

        Instant now = clock.instant();

        PremiumResponse.ScoreDetail scoreDetail = null;
        if (score != null) {
            scoreDetail = new PremiumResponse.ScoreDetail(
                    score.usageScore(),
                    score.maintenanceScore(),
                    effectiveComposite >= 0 ? effectiveComposite : null,
                    score.healthStatus(),
                    score.vehicleHealthScore(),
                    score.healthConfidence(),
                    score.renewalRecommendation(),
                    score.hasOpenRecall(),
                    score.dataFlags(),
                    score.contributingFactors()
            );
        }

        String methodologyNote = "Dynamic Risk Multiplier is illustrative, pending actuarial validation on real claims data (Phase A).";

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
                now.toString(),
                methodologyNote,
                scoreDetail,
                basis
        );
    }

    /**
     * Computes standard non-personalised pricing when consent is not established or vehicle is unregistered.
     */
    public PremiumResponse computeStatic(
            String vehicleId,
            String requestId,
            String policyId,
            PricingBasis basis,
            List<String> dataFlags,
            boolean isUnavailable) {

        // 1. IDV from pricing basis
        double idv = basis.idv();

        // 2. Base OD premium before NCB
        double basePremiumBeforeNcb = round2(idv * BASE_OD_RATE);

        // 3. NCB discount
        double ncbPct = basis.ncbPct();
        double ncbAdjPct = -ncbPct * 100;
        double basePremiumAfterNcb = round2(basePremiumBeforeNcb * (1 - ncbPct));

        List<DiscountBreakdown> breakdown = new ArrayList<>();
        if (ncbPct > 0) {
            breakdown.add(new DiscountBreakdown("no_claim_bonus", ncbAdjPct));
        }
        breakdown.add(new DiscountBreakdown("standard_pricing_no_consent", 0.0));

        double dynamicMultiplier = 1.0;
        double finalPremium = round2(basePremiumAfterNcb * dynamicMultiplier);

        String renewalRec = isUnavailable
                ? "We couldn't check your tracking preferences right now, so standard pricing is shown."
                : "Turn on usage and maintenance tracking to get a personalised premium and vehicle health recommendations.";

        List<ScoreResponse.ContributingFactor> factors = List.of(
                new ScoreResponse.ContributingFactor(
                        "tracking_off",
                        0.0,
                        "neutral",
                        "Tracking is disabled or consent could not be established."
                )
        );

        PremiumResponse.ScoreDetail scoreDetail = new PremiumResponse.ScoreDetail(
                null,
                null,
                null,
                "not_evaluated",
                null,
                null,
                renewalRec,
                null,
                dataFlags,
                factors
        );

        Instant now = clock.instant();
        String methodologyNote = "Standard non-personalised pricing applied (tracking consent not established).";

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
                now.toString(),
                methodologyNote,
                scoreDetail,
                basis
        );
    }

    // ---- Helpers ------------------------------------------------------------

    public double depreciation(int ageYears) {
        if (ageYears <= 0) return 0.95;
        if (ageYears > 5) return DEPRECIATION_BEYOND_5_YEARS;
        return DEPRECIATION_TABLE.getOrDefault(ageYears, DEPRECIATION_BEYOND_5_YEARS);
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double round3(double v) {
        return BigDecimal.valueOf(v).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
