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

    public int predictWaitTime(int patientsAhead, int avgConsultationTime, List<Integer> recentActualWaitTimes, double noShowRate) {
        // History is deliberately NOT used yet: actualWaitTime measures booking -> completion
        // (one patient's total wait), not consultation length, so it cannot be scaled by queue
        // position — using it gave patient #1 and #10 the same estimate (e.g. 395 min for #2).
        // It returns once the Doctor portal records consultation start/end times.
        // Until then: patients ahead, discounted by this doctor's no-show rate, x avg consultation time.
        int expectedPatientsAhead = (int) Math.round(patientsAhead * (1 - noShowRate));
        return expectedPatientsAhead * avgConsultationTime;
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
        String normalized = symptomDescription.toLowerCase();
        boolean isEmergency = EMERGENCY_KEYWORDS.stream().anyMatch(normalized::contains);
        return isEmergency ? Priority.EMERGENCY : Priority.NORMAL;
    }

}

