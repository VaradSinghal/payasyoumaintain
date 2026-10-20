package com.paymu.identity.service;

import com.paymu.identity.model.ConsentRecord;
import com.paymu.identity.model.OwnerDetails;
import com.paymu.identity.model.VehicleRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Seeds identity and consent records for the 5 demo vehicles on application startup
 * if demo.seed-on-startup is true.
 */
@Component
public class DemoSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private final RegistrationService registrationService;
    private final Clock clock;
    private final ZoneId zoneId;

    @Value("${demo.seed-on-startup:false}")
    private boolean seedOnStartup;

    public DemoSeeder(RegistrationService registrationService) {
        this(registrationService, Clock.system(ZoneId.of("Asia/Kolkata")), "Asia/Kolkata");
    }

    @Autowired
    public DemoSeeder(
            RegistrationService registrationService,
            Clock clock,
            @Value("${identity.timezone:Asia/Kolkata}") String timezone) {
        this.registrationService = registrationService;
        this.clock = clock;
        this.zoneId = ZoneId.of(timezone);
    }

    private record DemoVehicleSeed(
            String vehicleId,
            String ownerName,
            String rcNumber,
            String kycDocNumber,
            String kycDocType,
            String email,
            String phone,
            int ageYears,
            int ageMonths,
            String vehicleSegmentId
    ) {}

    private static final List<DemoVehicleSeed> DEMO_VEHICLES = List.of(
            new DemoVehicleSeed(
                    "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    "Aarav Sharma",
                    "MH02AB1234",
                    "987654321012",
                    "AADHAAR",
                    "aarav.sharma@example.com",
                    "9876543210",
                    2,
                    0,
                    "sedan_mid"
            ),
            new DemoVehicleSeed(
                    "b2c3d4e5-f6a7-8901-bcde-f12345678901",
                    "Priya Patel",
                    "DL01CD5678",
                    "ABCDE1234F",
                    "PAN",
                    "priya.patel@example.com",
                    "9876543211",
                    4,
                    0,
                    "hatchback_compact"
            ),
            new DemoVehicleSeed(
                    "c3d4e5f6-a7b8-9012-cdef-123456789012",
                    "Vikram Malhotra",
                    "KA03EF9012",
                    "876543210987",
                    "AADHAAR",
                    "vikram.malhotra@example.com",
                    "9876543212",
                    3,
                    0,
                    "suv_premium"
            ),
            new DemoVehicleSeed(
                    "d4e5f6a7-b8c9-0123-defa-234567890123",
                    "Sunita Rao",
                    "TN07GH3456",
                    "FGHIJ5678K",
                    "PAN",
                    "sunita.rao@example.com",
                    "9876543213",
                    5,
                    0,
                    "sedan_entry"
            ),
            new DemoVehicleSeed(
                    "e5f6a7b8-c9d0-1234-efab-345678901234",
                    "Rohan Iyer",
                    "GJ01JK7890",
                    "765432109876",
                    "AADHAAR",
                    "rohan.iyer@example.com",
                    "9876543214",
                    0,
                    3,
                    "suv_luxury"
            )
    );

    @Override
    public void run(ApplicationArguments args) {
        if (seedOnStartup) {
            seedDemoVehicles();
        }
    }

    public void seedDemoVehicles() {
        log.info("demo.seed-on-startup is enabled. Seeding identity and consent demo data...");
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        Instant now = Instant.now(clock);

        for (DemoVehicleSeed seed : DEMO_VEHICLES) {
            String policyId = UUID.nameUUIDFromBytes(
                    ("policy_" + seed.vehicleId()).getBytes(StandardCharsets.UTF_8)
            ).toString();

            System.out.println("Seeded demo vehicle: " + seed.vehicleId() + " -> policy_id: " + policyId);
            log.info("Demo vehicle seed: {} -> policy_id: {}", seed.vehicleId(), policyId);

            if (registrationService.getRegistration(seed.vehicleId()) == null) {
                LocalDate regDate = seed.ageYears() > 0
                        ? today.minusYears(seed.ageYears())
                        : today.minusMonths(seed.ageMonths());

                OwnerDetails owner = new OwnerDetails(
                        seed.ownerName(),
                        seed.kycDocNumber(),
                        seed.kycDocType(),
                        seed.email(),
                        seed.phone()
                );

                VehicleRegistration reg = new VehicleRegistration(
                        seed.vehicleId(),
                        policyId,
                        seed.rcNumber(),
                        regDate.toString(),
                        owner,
                        "VERIFIED",
                        now,
                        seed.vehicleSegmentId()
                );

                ConsentRecord consent = new ConsentRecord(
                        seed.vehicleId(),
                        true,
                        true,
                        now
                );

                registrationService.seedVehicle(reg, consent);
            }
        }
        log.info("Identity and consent demo data seeded successfully.");
    }
}
