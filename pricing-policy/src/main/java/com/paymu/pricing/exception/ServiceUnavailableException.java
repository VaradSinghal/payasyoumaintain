package com.paymu.pricing.exception;

/**
 * Thrown when an essential upstream service (e.g. identity-consent) is unavailable
 * and no cached fallback exists.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
