package com.paymu.claims.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Driver-reported cause of the incident.
 * The triage logic only distinguishes {@code MECHANICAL_FAILURE} from everything else;
 * remaining values are present for completeness and future routing.
 */
public enum ClaimedCause {

    MECHANICAL_FAILURE("mechanical_failure"),
    COLLISION("collision"),
    THEFT("theft"),
    NATURAL_DISASTER("natural_disaster"),
    VANDALISM("vandalism"),
    OTHER("other");

    private final String value;

    ClaimedCause(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
