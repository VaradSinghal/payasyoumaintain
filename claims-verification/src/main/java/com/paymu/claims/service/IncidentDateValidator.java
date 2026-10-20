package com.paymu.claims.service;

import com.paymu.claims.exception.InvalidIncidentDateException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

@Component
public class IncidentDateValidator {

    private final int maxIncidentAgeDays;
    private final ZoneId zoneId;
    private final Clock clock;

    @Autowired
    public IncidentDateValidator(
            @Value("${claims.max-incident-age-days:365}") int maxIncidentAgeDays,
            @Value("${claims.timezone:Asia/Kolkata}") String timezone,
            Clock clock) {
        this.maxIncidentAgeDays = maxIncidentAgeDays;
        this.zoneId = ZoneId.of(timezone);
        this.clock = clock;
    }

    public IncidentDateValidator(int maxIncidentAgeDays) {
        this(maxIncidentAgeDays, "Asia/Kolkata", Clock.systemUTC());
    }

    public IncidentDateValidator(int maxIncidentAgeDays, Clock clock, ZoneId zoneId) {
        this.maxIncidentAgeDays = maxIncidentAgeDays;
        this.zoneId = zoneId;
        this.clock = clock;
    }

    public void validate(String incidentDateStr) {
        if (incidentDateStr == null || incidentDateStr.isBlank()) {
            throw new InvalidIncidentDateException("incident_date is required");
        }

        LocalDate date;
        try {
            if (incidentDateStr.contains("T")) {
                try {
                    date = OffsetDateTime.parse(incidentDateStr).toLocalDate();
                } catch (DateTimeParseException e) {
                    date = LocalDateTime.parse(incidentDateStr).toLocalDate();
                }
            } else {
                date = LocalDate.parse(incidentDateStr);
            }
        } catch (DateTimeParseException e) {
            throw new InvalidIncidentDateException(
                    "incident_date '" + incidentDateStr + "' is not a valid ISO date.");
        }

        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        if (date.isAfter(today)) {
            throw new InvalidIncidentDateException(
                    "incident_date '" + incidentDateStr + "' cannot be in the future.");
        }

        LocalDate oldestAllowed = today.minusDays(maxIncidentAgeDays);
        if (date.isBefore(oldestAllowed)) {
            throw new InvalidIncidentDateException(
                    "incident_date '" + incidentDateStr + "' is older than " + maxIncidentAgeDays + " days.");
        }
    }
}
