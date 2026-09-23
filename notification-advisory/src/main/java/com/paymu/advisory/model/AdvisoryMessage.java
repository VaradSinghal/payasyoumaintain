package com.paymu.advisory.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single advisory message surfaced to the vehicle owner.
 *
 * @param advisoryType machine-readable type key (e.g. {@code "OPEN_RECALL"})
 * @param priority     display priority
 * @param title        short one-line heading suitable for a push notification
 * @param message      full plain-language explanation suitable for SMS / app body
 * @param actionUrl    optional deep-link or URL for the owner to act (e.g. book service)
 */
public record AdvisoryMessage(
        @JsonProperty("advisory_type") String advisoryType,
        AdvisoryPriority priority,
        String title,
        String message,
        @JsonProperty("action_url") String actionUrl
) {}
