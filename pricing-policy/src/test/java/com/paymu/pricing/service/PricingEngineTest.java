package com.paymu.pricing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paymu.pricing.config.PricingProperties;
import com.paymu.pricing.exception.ServiceUnavailableException;
import com.paymu.pricing.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PricingEngine Unit Tests")
class PricingEngineTest {

    private PricingEngine engine;
    private VehicleCatalogueService catalogueService;
    private PricingProperties properties;
    private Clock clock;

    private static final String DEMO_A1B2 = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String DEMO_B2C3 = "b2c3d4e5-f6a7-8901-bcde-f12345678901";
    private static final String DEMO_C3D4 = "c3d4e5f6-a7b8-9012-cdef-123456789012";
    private static final String DEMO_D4E5 = "d4e5f6a7-b8c9-0123-defa-234567890123";
    private static final String DEMO_E5F6 = "e5f6a7b8-c9d0-1234-efab-345678901234";

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-20T10:00:00Z"), ZoneId.of("Asia/Kolkata"));
        catalogueService = new VehicleCatalogueService(new ObjectMapper());
        catalogueService.init();

        properties = new PricingProperties();
        properties.setTimezone("Asia/Kolkata");
        properties.setProfileCacheTtlMinutes(60);
        properties.setDemoNcb(Map.of(
                DEMO_A1B2, 20.0,
                DEMO_B2C3, 10.0,
                DEMO_C3D4, 0.0,
                DEMO_D4E5, 25.0,
                DEMO_E5F6, 0.0
        ));

        engine = new PricingEngine(catalogueService, properties, clock);
    }

    // ─── Depreciation Slabs 0 to 6 & Age from Registration Date ──────────────

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
    @DisplayName("Age and slab for every age 0 to 6 derived correctly from registration date")
    void ageAndSlab_forEveryAge0To6_fromRegistrationDate() {
        LocalDate today = LocalDate.now(clock);
        double[] expectedDep = {0.95, 0.85, 0.80, 0.70, 0.60, 0.50, 0.40};
        long exShowroom = 1_200_000L; // sedan_mid

        for (int age = 0; age <= 6; age++) {
            LocalDate regDate = today.minusYears(age);
            String vid = "v-age-" + age;
            VehicleProfileResponse profile = new VehicleProfileResponse(
                    vid, "pol-" + age, regDate, "sedan_mid",
                    "VIN", "MH", "Hyundai", "Verna", "SX", 2026 - age
            );
            PricingBasis basis = engine.derivePricingBasis(vid, ProfileLookupResult.found(profile));

            assertEquals(age, basis.vehicleAgeYears(), "Age for " + age + " years ago");
            assertEquals(expectedDep[age], basis.depreciationFactor(), 0.001, "Depreciation for age " + age);
            double expectedIdv = exShowroom * expectedDep[age];
            assertEquals(expectedIdv, basis.idv(), 0.01, "IDV for age " + age);

            int ageMonths = engine.calculateAgeMonths(regDate);
            assertEquals(age * 12, ageMonths, "Age in months for age " + age);
        }
    }

    @Test
    @DisplayName("Age is never negative: future registration dates treated as age 0 without exception")
    void futureRegistrationDate_treatedAsAge0_neverNegativeOrException() {
        LocalDate today = LocalDate.now(clock);
        String vid = "v-future";

        // 1 day in the future
        LocalDate tomorrow = today.plusDays(1);
        VehicleProfileResponse profileTomorrow = new VehicleProfileResponse(
                vid, "pol-fut-1", tomorrow, "sedan_mid",
                "VIN", "MH", "Hyundai", "Verna", "SX", 2026
        );
        PricingBasis basisTomorrow = assertDoesNotThrow(() ->
                engine.derivePricingBasis(vid, ProfileLookupResult.found(profileTomorrow)));
        assertEquals(0, basisTomorrow.vehicleAgeYears(), "Future date must clamp to age 0");
        assertEquals(0.95, basisTomorrow.depreciationFactor(), 0.001, "Age 0 depreciation must apply");
        assertEquals(0, engine.calculateAgeMonths(tomorrow), "Age in months must be 0");

        // 2 years in the future
        LocalDate futureYear = today.plusYears(2);
        VehicleProfileResponse profileFutureYear = new VehicleProfileResponse(
                vid, "pol-fut-2", futureYear, "sedan_mid",
                "VIN", "MH", "Hyundai", "Verna", "SX", 2028
        );
        PricingBasis basisFutureYear = assertDoesNotThrow(() ->
                engine.derivePricingBasis(vid, ProfileLookupResult.found(profileFutureYear)));
        assertEquals(0, basisFutureYear.vehicleAgeYears(), "Future date next year must clamp to age 0");
        assertEquals(0.95, basisFutureYear.depreciationFactor(), 0.001);
        assertEquals(0, engine.calculateAgeMonths(futureYear), "Age in months must be 0");
    }

    // ─── Four Outcomes ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Outcome (a): Registered vehicle with known segment produces 'registered' source (or 'demo_profile' for demo IDs)")
    void outcomeA_knownSegmentRegisteredAndDemo() {
        LocalDate today = LocalDate.now(clock); // 2026-10-20

        // Non-demo vehicle: sedan_mid registered 2 years ago
        String nonDemoId = UUID.randomUUID().toString();
        VehicleProfileResponse profileNonDemo = new VehicleProfileResponse(
                nonDemoId, "pol-1", today.minusYears(2), "sedan_mid",
                "VIN1", "MH01", "Hyundai", "Verna", "SX", 2024
        );
        PricingBasis basisNonDemo = engine.derivePricingBasis(nonDemoId, ProfileLookupResult.found(profileNonDemo));
        assertEquals("registered", basisNonDemo.source());
        assertEquals("sedan_mid", basisNonDemo.vehicleSegmentId());
        assertEquals(1_200_000L, basisNonDemo.exShowroomPriceInr());
        assertEquals(2, basisNonDemo.vehicleAgeYears());
        assertEquals(0.80, basisNonDemo.depreciationFactor(), 0.001);
        assertEquals(960_000.0, basisNonDemo.idv(), 0.001);
        assertEquals(0.0, basisNonDemo.ncbPct(), 0.001);

        // Demo vehicle: a1b2 sedan_mid registered 2 years ago
        VehicleProfileResponse profileA1B2 = new VehicleProfileResponse(
                DEMO_A1B2, "pol-demo", today.minusYears(2), "sedan_mid",
                "VIN2", "MH02", "Hyundai", "Verna", "SX", 2024
        );
        PricingBasis basisA1B2 = engine.derivePricingBasis(DEMO_A1B2, ProfileLookupResult.found(profileA1B2));
        assertEquals("demo_profile", basisA1B2.source());
        assertEquals(20.0, basisA1B2.ncbPct(), 0.001);
    }

    @Test
    @DisplayName("Outcome (b): Found but segment missing or unknown produces 'generic_default' at 750,000 with REAL age")
    void outcomeB_missingOrUnknownSegmentUsesRealAge() {
        LocalDate today = LocalDate.now(clock);
        String vid = UUID.randomUUID().toString();

        // Unknown segment registered 4 years ago
        VehicleProfileResponse profileUnknown = new VehicleProfileResponse(
                vid, "pol-unk", today.minusYears(4), "unknown_segment_xyz",
                "VIN", "MH01", "Make", "Model", "Var", 2022
        );
        PricingBasis basisUnknown = engine.derivePricingBasis(vid, ProfileLookupResult.found(profileUnknown));
        assertEquals("generic_default", basisUnknown.source());
        assertEquals("unknown_segment_xyz", basisUnknown.vehicleSegmentId());
        assertEquals(750_000L, basisUnknown.exShowroomPriceInr());
        assertEquals(4, basisUnknown.vehicleAgeYears());
        assertEquals(0.60, basisUnknown.depreciationFactor(), 0.001);
        assertEquals(450_000.0, basisUnknown.idv(), 0.001);

        // Missing (null) segment registered 1 year ago
        VehicleProfileResponse profileNull = new VehicleProfileResponse(
                vid, "pol-null", today.minusYears(1), null,
                "VIN", "MH01", "Make", "Model", "Var", 2025
        );
        PricingBasis basisNull = engine.derivePricingBasis(vid, ProfileLookupResult.found(profileNull));
        assertEquals("generic_default", basisNull.source());
        assertNull(basisNull.vehicleSegmentId());
        assertEquals(750_000L, basisNull.exShowroomPriceInr());
        assertEquals(1, basisNull.vehicleAgeYears());
        assertEquals(0.85, basisNull.depreciationFactor(), 0.001);
        assertEquals(637_500.0, basisNull.idv(), 0.001);
    }

    @Test
    @DisplayName("Outcome (c): Identity answers 404 (unregistered) produces fixed generic profile (750,000, age 3)")
    void outcomeC_unregisteredFixedGenericProfile() {
        String unregId = UUID.randomUUID().toString();
        PricingBasis basis = engine.derivePricingBasis(unregId, ProfileLookupResult.unregistered());

        assertEquals("generic_default", basis.source());
        assertNull(basis.vehicleSegmentId());
        assertEquals(750_000L, basis.exShowroomPriceInr());
        assertEquals(3, basis.vehicleAgeYears());
        assertEquals(0.70, basis.depreciationFactor(), 0.001);
        assertEquals(525_000.0, basis.idv(), 0.001);
        assertEquals(0.0, basis.ncbPct(), 0.001);

        PremiumResponse staticResp = engine.computeStatic(unregId, "rid", "pid", basis, List.of("vehicle_not_registered"), false);
        assertEquals(13650.00, staticResp.basePremium(), 0.01);
        assertEquals(13650.00, staticResp.finalPremium(), 0.01);
        assertEquals(1.0, staticResp.dynamicMultiplier(), 0.001);
        assertEquals("not_evaluated", staticResp.scoreDetail().healthStatus());
        assertTrue(staticResp.scoreDetail().dataFlags().contains("vehicle_not_registered"));
    }

    @Test
    @DisplayName("Outcome (d): Identity unreachable / 5xx with no cached profile throws ServiceUnavailableException")
    void outcomeD_unreachableWithoutCacheThrows503() {
        String vid = UUID.randomUUID().toString();
        assertThrows(ServiceUnavailableException.class, () -> engine.derivePricingBasis(vid, ProfileLookupResult.unavailable()));
        assertThrows(ServiceUnavailableException.class, () -> engine.derivePricingBasis(vid, null));
    }

    // ─── Five Demo Quotes Unchanged (Exact Paisa) ─────────────────────────────

    @Test
    @DisplayName("The five demo quotes match required base and final premiums to the exact paisa")
    void fiveDemoQuotesUnchanged() {
        LocalDate today = LocalDate.now(clock);

        // 1. a1b2: sedan_mid, 2yr, 20% NCB -> base 24,960.00, final 13,977.60
        VehicleProfileResponse p1 = new VehicleProfileResponse(
                DEMO_A1B2, "pol-1", today.minusYears(2), "sedan_mid",
                "VIN1", "MH1", "Make", "Model", "Var", 2024
        );
        PricingBasis b1 = engine.derivePricingBasis(DEMO_A1B2, ProfileLookupResult.found(p1));
        ScoreResponse s1 = score(100.0, 100.0, 100.0); // composite 100 -> multiplier 0.700
        PremiumResponse r1 = engine.compute(DEMO_A1B2, "r1", "p1", b1, s1);
        assertEquals(24960.00, r1.basePremium(), 0.01, "a1b2 base premium");
        assertEquals(0.700, r1.dynamicMultiplier(), 0.001, "a1b2 dynamic multiplier");
        assertEquals(13977.60, r1.finalPremium(), 0.01, "a1b2 final premium");
        assertEquals("demo_profile", r1.pricingBasis().source());
        assertEquals(20.0, r1.pricingBasis().ncbPct(), 0.001, "a1b2 ncb_pct");

        // 2. b2c3: hatchback_compact, 4yr, 10% NCB -> base 12,480.00, final 12,265.34
        VehicleProfileResponse p2 = new VehicleProfileResponse(
                DEMO_B2C3, "pol-2", today.minusYears(4), "hatchback_compact",
                "VIN2", "MH2", "Make", "Model", "Var", 2022
        );
        PricingBasis b2 = engine.derivePricingBasis(DEMO_B2C3, ProfileLookupResult.found(p2));
        ScoreResponse s2 = score(75.0, 15.0, 51.0); // composite 51 -> multiplier 1.092
        PremiumResponse r2 = engine.compute(DEMO_B2C3, "r2", "p2", b2, s2);
        assertEquals(12480.00, r2.basePremium(), 0.01, "b2c3 base premium");
        assertEquals(1.092, r2.dynamicMultiplier(), 0.001, "b2c3 dynamic multiplier");
        assertEquals(12265.34, r2.finalPremium(), 0.01, "b2c3 final premium");
        assertEquals("demo_profile", r2.pricingBasis().source());
        assertEquals(10.0, r2.pricingBasis().ncbPct(), 0.001, "b2c3 ncb_pct");

        // 3. c3d4: suv_premium, 3yr, 0% NCB -> base 27,300.00, final 21,075.60
        VehicleProfileResponse p3 = new VehicleProfileResponse(
                DEMO_C3D4, "pol-3", today.minusYears(3), "suv_premium",
                "VIN3", "MH3", "Make", "Model", "Var", 2023
        );
        PricingBasis b3 = engine.derivePricingBasis(DEMO_C3D4, ProfileLookupResult.found(p3));
        ScoreResponse s3 = score(85.0, 100.0, 91.0); // composite 91 -> multiplier 0.772
        PremiumResponse r3 = engine.compute(DEMO_C3D4, "r3", "p3", b3, s3);
        assertEquals(27300.00, r3.basePremium(), 0.01, "c3d4 base premium");
        assertEquals(0.772, r3.dynamicMultiplier(), 0.001, "c3d4 dynamic multiplier");
        assertEquals(21075.60, r3.finalPremium(), 0.01, "c3d4 final premium");
        assertEquals("demo_profile", r3.pricingBasis().source());
        assertEquals(0.0, r3.pricingBasis().ncbPct(), 0.001, "c3d4 ncb_pct");

        // 4. d4e5: sedan_entry, 5yr, 25% NCB -> base 11,700.00, final 6,563.70
        VehicleProfileResponse p4 = new VehicleProfileResponse(
                DEMO_D4E5, "pol-4", today.minusYears(5), "sedan_entry",
                "VIN4", "MH4", "Make", "Model", "Var", 2021
        );
        PricingBasis b4 = engine.derivePricingBasis(DEMO_D4E5, ProfileLookupResult.found(p4));
        ScoreResponse s4 = score(100.0, 85.0, 94.0); // composite 94 -> multiplier 0.748
        PremiumResponse r4 = engine.compute(DEMO_D4E5, "r4", "p4", b4, s4);
        assertEquals(11700.00, r4.basePremium(), 0.01, "d4e5 base premium");
        assertEquals(0.748, r4.dynamicMultiplier(), 0.001, "d4e5 dynamic multiplier");
        assertEquals(6563.70, r4.finalPremium(), 0.01, "d4e5 final premium");
        assertEquals("demo_profile", r4.pricingBasis().source());
        assertEquals(25.0, r4.pricingBasis().ncbPct(), 0.001, "d4e5 ncb_pct");

        // 5. e5f6: suv_luxury, 3 months (< 1yr -> 0yr), 0% NCB -> base 44,460.00, final 44,460.00
        VehicleProfileResponse p5 = new VehicleProfileResponse(
                DEMO_E5F6, "pol-5", today.minusMonths(3), "suv_luxury",
                "VIN5", "MH5", "Make", "Model", "Var", 2026
        );
        PricingBasis b5 = engine.derivePricingBasis(DEMO_E5F6, ProfileLookupResult.found(p5));
        PremiumResponse r5 = engine.compute(DEMO_E5F6, "r5", "p5", b5, null); // neutral multiplier 1.0
        assertEquals(44460.00, r5.basePremium(), 0.01, "e5f6 base premium");
        assertEquals(1.000, r5.dynamicMultiplier(), 0.001, "e5f6 dynamic multiplier");
        assertEquals(44460.00, r5.finalPremium(), 0.01, "e5f6 final premium");
        assertEquals("demo_profile", r5.pricingBasis().source());
        assertEquals(0.0, r5.pricingBasis().ncbPct(), 0.001, "e5f6 ncb_pct");
    }

    // ─── Static Path with Cached Profile ─────────────────────────────────────

    @Test
    @DisplayName("a1b2 static path with consent_status_unavailable produces 19,968.00 with pricing_basis")
    void a1b2StaticPath_produces19968() {
        LocalDate today = LocalDate.now(clock);
        VehicleProfileResponse p1 = new VehicleProfileResponse(
                DEMO_A1B2, "pol-1", today.minusYears(2), "sedan_mid",
                "VIN1", "MH1", "Make", "Model", "Var", 2024
        );
        PricingBasis b1 = engine.derivePricingBasis(DEMO_A1B2, ProfileLookupResult.found(p1));
        PremiumResponse staticResp = engine.computeStatic(DEMO_A1B2, "rid", "pid", b1, List.of("consent_status_unavailable"), true);

        assertEquals(24960.00, staticResp.basePremium(), 0.01);
        assertEquals(1.0, staticResp.dynamicMultiplier(), 0.001);
        assertEquals(19968.00, staticResp.finalPremium(), 0.01);
        assertNotNull(staticResp.pricingBasis());
        assertEquals("demo_profile", staticResp.pricingBasis().source());
        assertEquals(1_200_000L, staticResp.pricingBasis().exShowroomPriceInr());
        assertEquals(20.0, staticResp.pricingBasis().ncbPct(), 0.001);
    }

    private ScoreResponse score(Double usage, Double maintenance, Double composite) {
        return new ScoreResponse("s-123", "v-123",
                usage, maintenance, composite,
                "2026-10-20T10:00:00Z", "v1", "scored", maintenance, "verified", "renew",
                false, List.of(), List.of());
    }
}
