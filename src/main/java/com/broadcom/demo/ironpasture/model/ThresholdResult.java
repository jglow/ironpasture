package com.broadcom.demo.ironpasture.model;

public record ThresholdResult(
        String fieldName,
        String status,
        double observedValue,
        double thresholdValue,
        double margin,
        String pmoReference
) {

    public static ThresholdResult pass(String fieldName, double observed, double threshold, String pmoRef) {
        return new ThresholdResult(fieldName, "PASS", observed, threshold, threshold - observed, pmoRef);
    }

    public static ThresholdResult fail(String fieldName, double observed, double threshold, String pmoRef) {
        return new ThresholdResult(fieldName, "FAIL", observed, threshold, observed - threshold, pmoRef);
    }
}
