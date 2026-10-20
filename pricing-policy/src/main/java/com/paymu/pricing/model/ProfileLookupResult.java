package com.paymu.pricing.model;

/**
 * Result of resolving a vehicle profile from identity-consent / cache.
 */
public record ProfileLookupResult(
        Status status,
        VehicleProfileResponse profile
) {
    public enum Status {
        FOUND,
        UNREGISTERED,
        UNAVAILABLE
    }

    public static ProfileLookupResult found(VehicleProfileResponse profile) {
        return new ProfileLookupResult(Status.FOUND, profile);
    }

    public static ProfileLookupResult unregistered() {
        return new ProfileLookupResult(Status.UNREGISTERED, null);
    }

    public static ProfileLookupResult unavailable() {
        return new ProfileLookupResult(Status.UNAVAILABLE, null);
    }

    public boolean isFound() {
        return status == Status.FOUND;
    }

    public boolean isUnregistered() {
        return status == Status.UNREGISTERED;
    }

    public boolean isUnavailable() {
        return status == Status.UNAVAILABLE;
    }
}
