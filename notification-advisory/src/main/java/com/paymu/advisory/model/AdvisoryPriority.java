package com.paymu.advisory.model;

/**
 * Priority of an advisory message, used to sort the list returned to the client.
 * Higher priority items appear first.
 */
public enum AdvisoryPriority {
    /** Immediate action required — open recall, safety risk. */
    CRITICAL,
    /** Action should be taken soon — service overdue, premium surcharge applied. */
    HIGH,
    /** Informational — score trending down, upcoming service due. */
    MEDIUM,
    /** Positive reinforcement — safe driving, on-time service. */
    LOW
}
