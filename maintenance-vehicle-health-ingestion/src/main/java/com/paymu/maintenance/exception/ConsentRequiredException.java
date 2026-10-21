package com.paymu.maintenance.exception;

public class ConsentRequiredException extends RuntimeException {
    public ConsentRequiredException(String message) {
        super(message);
    }
}
