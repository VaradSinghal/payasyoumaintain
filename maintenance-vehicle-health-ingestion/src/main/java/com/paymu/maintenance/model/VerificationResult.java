package com.paymu.maintenance.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record VerificationResult(
        @JsonProperty("status") String status,
        @JsonProperty("checks") List<ConsistencyCheck> checks
) {}
