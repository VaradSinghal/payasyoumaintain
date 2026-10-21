package com.paymu.maintenance.service;

import com.paymu.maintenance.client.VehicleRegistrationResponse;
import com.paymu.maintenance.model.ConsistencyCheck;
import com.paymu.maintenance.model.MaintenanceEvent;
import com.paymu.maintenance.model.ServiceRecordRequest;
import com.paymu.maintenance.model.VerificationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsistencyCheckService Unit Tests")
class ConsistencyCheckServiceTest {

    private ZoneId istZone;
    private Clock fixedClock;
    private ConsistencyCheckService service;
    private UUID vehicleId;
    private VehicleRegistrationResponse registration;

    @BeforeEach
    void setUp() {
        istZone = ZoneId.of("Asia/Kolkata");
        // 2026-10-21 12:00:00 IST
        Instant instant = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, istZone).toInstant();
        fixedClock = Clock.fixed(instant, istZone);
        service = new ConsistencyCheckService(fixedClock, 1000, 5);
        vehicleId = UUID.randomUUID();
        registration = new VehicleRegistrationResponse(vehicleId, LocalDate.of(2024, 10, 21), "POL-123");
    }

    private Map<String, ConsistencyCheck> checksToMap(VerificationResult result) {
        return result.checks().stream().collect(Collectors.toMap(ConsistencyCheck::code, c -> c));
    }

    @Test
    @DisplayName("date_not_future: past and today pass; tomorrow in IST fails")
    void testDateNotFuture() {
        // Today in IST is 2026-10-21
        ServiceRecordRequest reqToday = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 21), "oil_change", 15000, "Notes", null, null);
        VerificationResult resToday = service.evaluate(reqToday, registration, List.of());
        assertTrue(checksToMap(resToday).get("date_not_future").passed());

        // Past date
        ServiceRecordRequest reqPast = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 20), "oil_change", 15000, "Notes", null, null);
        VerificationResult resPast = service.evaluate(reqPast, registration, List.of());
        assertTrue(checksToMap(resPast).get("date_not_future").passed());

        // Tomorrow in IST
        ServiceRecordRequest reqFuture = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 22), "oil_change", 15000, "Notes", null, null);
        VerificationResult resFuture = service.evaluate(reqFuture, registration, List.of());
        assertFalse(checksToMap(resFuture).get("date_not_future").passed());
        assertEquals("rejected", resFuture.status());
    }

    @Test
    @DisplayName("date_not_future: IST rollover test (20:00 UTC rolls over to next day in IST)")
    void testDateNotFutureWithIstRollover() {
        // 2026-10-21 20:00 UTC = 2026-10-22 01:30 IST
        Instant rolloverInstant = ZonedDateTime.of(2026, 10, 21, 20, 0, 0, 0, ZoneOffset.UTC).toInstant();
        Clock rolloverClock = Clock.fixed(rolloverInstant, istZone);
        ConsistencyCheckService rolloverService = new ConsistencyCheckService(rolloverClock, 1000, 5);

        // In IST, date is now 2026-10-22. So 2026-10-22 is today and should pass!
        ServiceRecordRequest req22nd = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 22), "oil_change", 15000, "Notes", null, null);
        VerificationResult res22nd = rolloverService.evaluate(req22nd, registration, List.of());
        assertTrue(checksToMap(res22nd).get("date_not_future").passed());

        // But 2026-10-23 is future and must fail!
        ServiceRecordRequest req23rd = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 23), "oil_change", 15000, "Notes", null, null);
        VerificationResult res23rd = rolloverService.evaluate(req23rd, registration, List.of());
        assertFalse(checksToMap(res23rd).get("date_not_future").passed());
    }

    @Test
    @DisplayName("date_after_registration: >= registration date passes; < registration date fails hard")
    void testDateAfterRegistration() {
        // Equal to registration date (2024-10-21)
        ServiceRecordRequest reqExact = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2024, 10, 21), "oil_change", 1000, "Notes", null, null);
        VerificationResult resExact = service.evaluate(reqExact, registration, List.of());
        assertTrue(checksToMap(resExact).get("date_after_registration").passed());

        // Day before registration date
        ServiceRecordRequest reqBefore = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2024, 10, 20), "oil_change", 1000, "Notes", null, null);
        VerificationResult resBefore = service.evaluate(reqBefore, registration, List.of());
        assertFalse(checksToMap(resBefore).get("date_after_registration").passed());
        assertEquals("rejected", resBefore.status());
    }

    @Test
    @DisplayName("odometer_monotonic: passes when monotonic, fails soft when non-monotonic")
    void testOdometerMonotonic() {
        MaintenanceEvent pastEvent = new MaintenanceEvent(
                "e1", vehicleId.toString(), "2026-09-01", "oil_change", 10000, List.of(), "oem_api", "notes", List.of());
        MaintenanceEvent futureEvent = new MaintenanceEvent(
                "e2", vehicleId.toString(), "2026-10-15", "oil_change", 14000, List.of(), "inspection", "notes", List.of());

        // Monotonic reading between 10000 and 14000
        ServiceRecordRequest reqValid = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 01), "brake_service", 12000, "Notes", null, null);
        VerificationResult resValid = service.evaluate(reqValid, registration, List.of(pastEvent, futureEvent));
        assertTrue(checksToMap(resValid).get("odometer_monotonic").passed());

        // Odometer lower than past reading
        ServiceRecordRequest reqLow = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 01), "brake_service", 9500, "Notes", null, null);
        VerificationResult resLow = service.evaluate(reqLow, registration, List.of(pastEvent, futureEvent));
        assertFalse(checksToMap(resLow).get("odometer_monotonic").passed());
        assertEquals("flagged", resLow.status());

        // Odometer higher than future reading
        ServiceRecordRequest reqHigh = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 01), "brake_service", 14500, "Notes", null, null);
        VerificationResult resHigh = service.evaluate(reqHigh, registration, List.of(pastEvent, futureEvent));
        assertFalse(checksToMap(resHigh).get("odometer_monotonic").passed());
        assertEquals("flagged", resHigh.status());
    }

    @Test
    @DisplayName("odometer_monotonic: ignores flagged self-reported readings")
    void testOdometerMonotonicIgnoresFlagged() {
        // Flagged self_upload event with bogus high odometer
        MaintenanceEvent flaggedEvent = new MaintenanceEvent(
                "e-flagged", vehicleId.toString(), "2026-09-01", "oil_change", 50000, List.of(), "self_upload", "notes", List.of(), "flagged");

        // Our reading at 15000 is monotonic because the 50000 was flagged and must be ignored
        ServiceRecordRequest req = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 01), "oil_change", 15000, "Notes", null, null);
        VerificationResult res = service.evaluate(req, registration, List.of(flaggedEvent));
        assertTrue(checksToMap(res).get("odometer_monotonic").passed());
        assertEquals("No neighbouring readings to compare for monotonicity.", checksToMap(res).get("odometer_monotonic").message());
    }

    @Test
    @DisplayName("odometer_rate_plausible: rate <= 1000 km/day passes; rate > 1000 km/day fails soft")
    void testOdometerRatePlausible() {
        MaintenanceEvent pastEvent = new MaintenanceEvent(
                "e1", vehicleId.toString(), "2026-10-19", "oil_change", 10000, List.of(), "oem_api", "notes", List.of());

        // 2 days later, +1500 km -> 750 km/day <= 1000 -> passes
        ServiceRecordRequest reqPass = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 21), "oil_change", 11500, "Notes", null, null);
        VerificationResult resPass = service.evaluate(reqPass, registration, List.of(pastEvent));
        assertTrue(checksToMap(resPass).get("odometer_rate_plausible").passed());

        // 2 days later, +2500 km -> 1250 km/day > 1000 -> fails soft
        ServiceRecordRequest reqFail = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 21), "oil_change", 12500, "Notes", null, null);
        VerificationResult resFail = service.evaluate(reqFail, registration, List.of(pastEvent));
        assertFalse(checksToMap(resFail).get("odometer_rate_plausible").passed());
        assertEquals("flagged", resFail.status());
    }

    @Test
    @DisplayName("not_duplicate: identical date/type/odometer or matching receipt_sha256 fails soft")
    void testNotDuplicate() {
        MaintenanceEvent stored = new MaintenanceEvent(
                "e1", vehicleId.toString(), "2026-10-15", "oil_change", 12000, List.of(), "self_upload", "notes", List.of(),
                "accepted", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", Instant.now(fixedClock));

        // Exact match on date, type, odometer
        ServiceRecordRequest reqSameEvent = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 15), "oil_change", 12000, "Different notes", null, null);
        VerificationResult resSame = service.evaluate(reqSameEvent, registration, List.of(stored));
        assertFalse(checksToMap(resSame).get("not_duplicate").passed());
        assertEquals("flagged", resSame.status());

        // Different date/odometer but matching receipt_sha256
        ServiceRecordRequest reqSameSha = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 16), "brake_service", 12500, "Notes", null,
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        VerificationResult resSha = service.evaluate(reqSameSha, registration, List.of(stored));
        assertFalse(checksToMap(resSha).get("not_duplicate").passed());
        assertEquals("flagged", resSha.status());
    }

    @Test
    @DisplayName("daily_limit: 5 records stored today pass; 6th record stored today fails soft")
    void testDailyLimit() {
        Instant now = Instant.now(fixedClock);
        List<MaintenanceEvent> fiveStoredToday = List.of(
                new MaintenanceEvent("e1", vehicleId.toString(), "2026-05-01", "oil_change", 1000, List.of(), "self_upload", "", List.of(), "accepted", null, now),
                new MaintenanceEvent("e2", vehicleId.toString(), "2026-06-01", "oil_change", 2000, List.of(), "self_upload", "", List.of(), "accepted", null, now),
                new MaintenanceEvent("e3", vehicleId.toString(), "2026-07-01", "oil_change", 3000, List.of(), "self_upload", "", List.of(), "accepted", null, now),
                new MaintenanceEvent("e4", vehicleId.toString(), "2026-08-01", "oil_change", 4000, List.of(), "self_upload", "", List.of(), "accepted", null, now),
                new MaintenanceEvent("e5", vehicleId.toString(), "2026-09-01", "oil_change", 5000, List.of(), "self_upload", "", List.of(), "accepted", null, now)
        );

        ServiceRecordRequest req6th = new ServiceRecordRequest(
                vehicleId, LocalDate.of(2026, 10, 20), "oil_change", 6000, "Notes", null, null);
        VerificationResult res = service.evaluate(req6th, registration, fiveStoredToday);
        assertFalse(checksToMap(res).get("daily_limit").passed());
        assertEquals("flagged", res.status());
    }
}
