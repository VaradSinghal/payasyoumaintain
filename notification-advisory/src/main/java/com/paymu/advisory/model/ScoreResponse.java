package com.paymu.advisory.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Mirrors score.schema.json from risk-scoring-engine. */
public record ScoreResponse(
        @JsonProperty("score_id") String scoreId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("usage_score") double usageScore,
        @JsonProperty("maintenance_score") double maintenanceScore,
        @JsonProperty("composite_score") Double compositeScore,
        @JsonProperty("computed_at") String computedAt,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("contributing_factors") List<ContributingFactor> contributingFactors
) {
    public record ContributingFactor(
            @JsonProperty("factor_name") String factorName,
            double impact,
            String direction,
            String description
    ) {}
}
