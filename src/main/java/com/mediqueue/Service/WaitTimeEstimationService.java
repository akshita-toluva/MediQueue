package com.mediqueue.Service;

import com.mediqueue.entity.Priority;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class WaitTimeEstimationService {

    private static final List<String> EMERGENCY_KEYWORDS = List.of(
            "chest pain",
            "severe bleeding",
            "heavy bleeding",
            "difficulty breathing",
            "difficulty in breathing",
            "loss of consciousness",
            "unconscious",
            "can't breathe",
            "cannot breathe"
    );

    // Word order varies ("pain in chest", "bleeding is heavy"): each set = words that must ALL appear, any order.
    // Over-triage is the safe failure: a doctor can mark it normal in one (logged) click.
    private static final List<Set<String>> EMERGENCY_WORD_SETS = List.of(
            Set.of("chest", "pain"),
            Set.of("chest", "pains"),
            Set.of("chest", "tightness"),
            Set.of("bleeding", "severe"),
            Set.of("bleeding", "heavy"),
            Set.of("breathing", "difficulty"),
            Set.of("breathing", "trouble"),
            Set.of("breathe", "unable"),
            Set.of("consciousness", "lost")
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
        // Check 1 (unchanged): exact phrases, ignoring spaces/punctuation ("ChestPain", "chest-pain")
        String text = lettersOnly(symptomDescription);
        boolean phraseMatch = EMERGENCY_KEYWORDS.stream()
                .map(WaitTimeEstimationService::lettersOnly)
                .anyMatch(text::contains);
        // Check 2 (new): the same danger described in a different word order
        Set<String> words = Arrays.stream(symptomDescription.toLowerCase().split("[^a-z]+"))
                .filter(w -> !w.isEmpty())
                .collect(Collectors.toSet());
        boolean wordSetMatch = EMERGENCY_WORD_SETS.stream().anyMatch(words::containsAll);
        return (phraseMatch || wordSetMatch) ? Priority.EMERGENCY : Priority.NORMAL;
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

