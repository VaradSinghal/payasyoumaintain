package com.paymu.telematics.model;

import java.util.List;

/**
 * Result of the aggregation pipeline: processed events (with trip IDs
 * assigned where they were missing) and the computed per-trip aggregates.
 */
public record AggregationResult(
        List<UsageEvent> processedEvents,
        List<TripAggregate> aggregates
) {}
