package com.mediqueue.dto;

import java.util.List;

public record WaitAccuracyResponse(int days, int toleranceMinutes, AccuracyStats overall,
                                   List<AccuracyStats> byDepartment, List<ErrorBucket> distribution) {
}
