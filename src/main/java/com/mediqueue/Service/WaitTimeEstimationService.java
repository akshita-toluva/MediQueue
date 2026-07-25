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

    public int predictWaitTime(int patientsAhead, int avgConsultationTime, List<Integer> recentActualWaitTimes) {
        if (recentActualWaitTimes == null || recentActualWaitTimes.isEmpty()) {
            // Cold start: no completed-consultation history for this doctor yet
            return patientsAhead * avgConsultationTime;
        }
        return recencyWeightedAverage(recentActualWaitTimes);
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

