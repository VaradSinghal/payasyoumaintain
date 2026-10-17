package com.paymu.claims.service;

import com.paymu.claims.exception.InvalidIncidentDateException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@Component
public class IncidentDateValidator {

    private final int maxIncidentAgeDays;

    public IncidentDateValidator(@Value("${claims.max-incident-age-days:365}") int maxIncidentAgeDays) {
        this.maxIncidentAgeDays = maxIncidentAgeDays;
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

        LocalDate today = LocalDate.now();
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
