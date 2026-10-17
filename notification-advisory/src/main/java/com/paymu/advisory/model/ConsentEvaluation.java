package com.paymu.advisory.model;

public record ConsentEvaluation(
        boolean isPermitted,
        boolean isUnavailable
) {
    public static ConsentEvaluation permitted() {
        return new ConsentEvaluation(true, false);
    }

    public static ConsentEvaluation denied() {
        return new ConsentEvaluation(false, false);
    }

    public static ConsentEvaluation unavailable() {
        return new ConsentEvaluation(false, true);
    }
}
