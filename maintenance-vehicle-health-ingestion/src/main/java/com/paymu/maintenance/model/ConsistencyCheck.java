package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ConsistencyCheck(
        @JsonProperty("code") String code,
        @JsonProperty("passed") boolean passed,
        @JsonProperty("message") String message
) {}
