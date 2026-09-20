package com.paymu.identity.model;

import jakarta.validation.constraints.NotNull;

public record ConsentUpdateRequest(
        @NotNull(message = "usageTrackingOptIn must be provided")
        Boolean usageTrackingOptIn,
        
        @NotNull(message = "maintenanceTrackingOptIn must be provided")
        Boolean maintenanceTrackingOptIn
) {}
