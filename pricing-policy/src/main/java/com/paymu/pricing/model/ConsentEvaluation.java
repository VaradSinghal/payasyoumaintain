package com.paymu.pricing.model;

import java.util.List;

public record ConsentEvaluation(
        boolean isPermitted,
        List<String> dataFlags,
        boolean isUnavailable
) {
    public static ConsentEvaluation permitted() {
        return new ConsentEvaluation(true, List.of(), false);
    }

    public static ConsentEvaluation denied(List<String> flags) {
        return new ConsentEvaluation(false, flags, false);
    }

    public static ConsentEvaluation unavailable(List<String> flags) {
        return new ConsentEvaluation(false, flags, true);
    }
}
