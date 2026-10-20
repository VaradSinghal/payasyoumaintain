package com.paymu.claims.service;

import com.paymu.claims.exception.InvalidIncidentDateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class IncidentDateValidatorTest {

    private final IncidentDateValidator validator = new IncidentDateValidator(365);

    @Test
    @DisplayName("Valid recent ISO date passes validation")
    void validRecentDate_passes() {
        String today = LocalDate.now(ZoneId.of("Asia/Kolkata")).toString();
        assertDoesNotThrow(() -> validator.validate(today));

        String fiveDaysAgo = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(5).toString();
        assertDoesNotThrow(() -> validator.validate(fiveDaysAgo));
    }

    @Test
    @DisplayName("Valid ISO date with timestamp passes validation")
    void validDateWithTimestamp_passes() {
        String isoDateTime = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(2) + "T10:00:00Z";
        assertDoesNotThrow(() -> validator.validate(isoDateTime));
    }

    @Test
    @DisplayName("Future incident date throws InvalidIncidentDateException")
    void futureDate_throws() {
        String tomorrow = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).toString();
        InvalidIncidentDateException ex = assertThrows(InvalidIncidentDateException.class,
                () -> validator.validate(tomorrow));
        assertTrue(ex.getMessage().contains("future"));
    }

    @Test
    @DisplayName("Incident date older than max age (365 days) throws InvalidIncidentDateException")
    void dateOlderThanMaxAge_throws() {
        String oldDate = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusDays(400).toString();
        InvalidIncidentDateException ex = assertThrows(InvalidIncidentDateException.class,
                () -> validator.validate(oldDate));
        assertTrue(ex.getMessage().contains("older than 365 days"));
    }

    @Test
    @DisplayName("Malformed date string throws InvalidIncidentDateException")
    void malformedDate_throws() {
        assertThrows(InvalidIncidentDateException.class, () -> validator.validate("not-a-date"));
        assertThrows(InvalidIncidentDateException.class, () -> validator.validate(""));
        assertThrows(InvalidIncidentDateException.class, () -> validator.validate(null));
    }

    @Test
    @DisplayName("Fixed Clock at 20:00 UTC (01:30 IST next day) validates using Asia/Kolkata date")
    void fixedClockAtNightUtc_validatesInAsiaKolkata() {
        // At 20:00 UTC on 2026-10-20, it is 01:30 IST on 2026-10-21
        Instant instant = Instant.parse("2026-10-20T20:00:00Z");
        Clock fixedClock = Clock.fixed(instant, ZoneOffset.UTC);
        IncidentDateValidator kolkataValidator = new IncidentDateValidator(365, "Asia/Kolkata", fixedClock);

        // IST date (2026-10-21) is accepted
        String istToday = "2026-10-21";
        assertDoesNotThrow(() -> kolkataValidator.validate(istToday));

        // IST date plus one day (2026-10-22) is rejected
        String istTomorrow = "2026-10-22";
        InvalidIncidentDateException futureEx = assertThrows(InvalidIncidentDateException.class,
                () -> kolkataValidator.validate(istTomorrow));
        assertTrue(futureEx.getMessage().contains("future"));

        // IST today minus 365 (2025-10-21) is accepted
        String oldestAllowed = "2025-10-21";
        assertDoesNotThrow(() -> kolkataValidator.validate(oldestAllowed));

        // IST today minus 366 (2025-10-20) is rejected
        String tooOld = "2025-10-20";
        InvalidIncidentDateException oldEx = assertThrows(InvalidIncidentDateException.class,
                () -> kolkataValidator.validate(tooOld));
        assertTrue(oldEx.getMessage().contains("older than 365 days"));
    }
}
