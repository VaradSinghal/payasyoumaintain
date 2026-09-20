package com.paymu.telematics.exception;

import java.util.List;

/**
 * Thrown when one or more events in a batch fail JSON Schema validation.
 */
public class SchemaValidationException extends RuntimeException {

    private final List<String> errors;

    public SchemaValidationException(List<String> errors) {
        super("Schema validation failed: " + errors.size() + " error(s)");
        this.errors = errors;
    }

    public List<String> getErrors() {
        return errors;
    }
}
