package com.mediqueue.Service;

import com.mediqueue.entity.Priority;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WaitTimeEstimationService {

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "chest pain",
            "severe bleeding",
            "difficulty breathing",
            "loss of consciousness",
            "unconscious",
            "can't breathe",
            "cannot breathe"
    );
    private static final int MIN_SAMPLES = 3;

    public int predictWaitTime(int patientsAhead, int avgConsultationTime,
                               List<Integer> recentConsultationMinutes, double noShowRate) {
        // Some of the patients ahead won't turn up: discount by this doctor's no-show rate.
        int expectedPatientsAhead = (int) Math.round(patientsAhead * (1 - noShowRate));
        return expectedPatientsAhead * learnedConsultationMinutes(avgConsultationTime, recentConsultationMinutes);
    }

    // recentActualWaitTimes must be ordered most-recent-first (index 0 = latest
    // completed consultation). Index 0 gets the highest weight, so a doctor who's
    // been running long today pulls the estimate up faster than a stale average would.
    private int recencyWeightedAverage(List<Integer> recentActualWaitTimes) {
        int n = recentActualWaitTimes.size();
        int weightedSum = 0;
        int weightTotal = 0;
        for (int i = 0; i < n; i++) {
            int weight = n - i;
            weightedSum += recentActualWaitTimes.get(i) * weight;
            weightTotal += weight;
        }
        return weightedSum / weightTotal;
    }

    public Priority classifyPriority(String symptomDescription) {
        if (symptomDescription == null) {
            return Priority.NORMAL;
        }
        String text = lettersOnly(symptomDescription);
        boolean isEmergency = EMERGENCY_KEYWORDS.stream()
                .map(WaitTimeEstimationService::lettersOnly)
                .anyMatch(text::contains);
        return isEmergency ? Priority.EMERGENCY : Priority.NORMAL;
    }

    private static String lettersOnly(String s) {
        return s.toLowerCase().replaceAll("[^a-z]", "");
    }

    int learnedConsultationMinutes(int configuredAvg, List<Integer> recentConsultationMinutes) {
        if (recentConsultationMinutes == null) return configuredAvg;
        int max = configuredAvg * 3;
        List<Integer> believable = recentConsultationMinutes.stream()
                .filter(m -> m >= 1 && m <= max)   // drop test clicks and forgotten "Complete" buttons
                .toList();
        if (believable.size() < MIN_SAMPLES) return configuredAvg;
        return recencyWeightedAverage(believable);
    }
}

