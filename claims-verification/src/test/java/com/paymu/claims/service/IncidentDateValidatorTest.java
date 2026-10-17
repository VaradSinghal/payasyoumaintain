package com.paymu.claims.service;

import com.paymu.claims.exception.InvalidIncidentDateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class IncidentDateValidatorTest {

    private final IncidentDateValidator validator = new IncidentDateValidator(365);

    @Test
    @DisplayName("Valid recent ISO date passes validation")
    void validRecentDate_passes() {
        String today = LocalDate.now().toString();
        assertDoesNotThrow(() -> validator.validate(today));

        String fiveDaysAgo = LocalDate.now().minusDays(5).toString();
        assertDoesNotThrow(() -> validator.validate(fiveDaysAgo));
    }

    @Test
    @DisplayName("Valid ISO date with timestamp passes validation")
    void validDateWithTimestamp_passes() {
        String isoDateTime = LocalDate.now().minusDays(2) + "T10:00:00Z";
        assertDoesNotThrow(() -> validator.validate(isoDateTime));
    }

    @Test
    @DisplayName("Future incident date throws InvalidIncidentDateException")
    void futureDate_throws() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        InvalidIncidentDateException ex = assertThrows(InvalidIncidentDateException.class,
                () -> validator.validate(tomorrow));
        assertTrue(ex.getMessage().contains("future"));
    }

    @Test
    @DisplayName("Incident date older than max age (365 days) throws InvalidIncidentDateException")
    void dateOlderThanMaxAge_throws() {
        String oldDate = LocalDate.now().minusDays(400).toString();
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
}
