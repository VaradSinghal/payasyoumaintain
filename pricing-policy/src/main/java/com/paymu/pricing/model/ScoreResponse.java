package com.paymu.pricing.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Mirrors contracts/schemas/score.schema.json */
public record ScoreResponse(
        @JsonProperty("score_id") String scoreId,
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("usage_score") double usageScore,
        @JsonProperty("maintenance_score") Double maintenanceScore,
        @JsonProperty("composite_score") Double compositeScore,
        @JsonProperty("computed_at") String computedAt,
        @JsonProperty("model_version") String modelVersion,
        @JsonProperty("health_status") String healthStatus,
        @JsonProperty("vehicle_health_score") Double vehicleHealthScore,
        @JsonProperty("health_confidence") String healthConfidence,
        @JsonProperty("renewal_recommendation") String renewalRecommendation,
        @JsonProperty("has_open_recall") Boolean hasOpenRecall,
        @JsonProperty("data_flags") List<String> dataFlags,
        @JsonProperty("contributing_factors") List<ContributingFactor> contributingFactors
) {
    public record ContributingFactor(
            @JsonProperty("factor_name") String factorName,
            double impact,
            String direction,
            String description
    ) {}
}
