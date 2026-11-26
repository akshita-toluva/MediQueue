package com.mediqueue.dto;

// Doubles are null when there are no visits: "no data" is different from "0% accurate"
public record AccuracyStats(String label, int visits, Double withinTolerancePct,
                            Double meanAbsErrorMin, Double biasMin) {
}
