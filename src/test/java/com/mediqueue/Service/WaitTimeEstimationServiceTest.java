package com.mediqueue.Service;

import com.mediqueue.entity.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WaitTimeEstimationServiceTest {

    // Plain object, no mocks: this class needs nothing injected
    private final WaitTimeEstimationService service = new WaitTimeEstimationService();

    @Test
    void noHistoryAtAll_usesConfiguredAverage() {
        assertEquals(10, service.learnedConsultationMinutes(10, null));
    }

    @Test
    void tooFewSamples_usesConfiguredAverage() {
        // max = 30 → both 0s dropped, 12 kept → only 1 sample (< 3) → fallback
        assertEquals(10, service.learnedConsultationMinutes(10, List.of(0, 0, 12)));
    }

    @Test
    void exactlyThreeSamples_learns(){
        assertEquals(20,service.learnedConsultationMinutes(10,List.of(20,20,20)));
    }

    @Test
    void twoSamples_fallsBack(){
        assertEquals(10,service.learnedConsultationMinutes(10,List.of(20,20)));
    }

    @Test
    void valueExactlyThreeTimesAverage_isKept(){
        assertEquals(30,service.learnedConsultationMinutes(10,List.of(30,30,30)));
    }

    @Test
    void valueAboveThreeTimesAverage_isIgnored(){
        assertEquals(10,service.learnedConsultationMinutes(10,List.of(31,20,20)));
    }

    @ParameterizedTest
    @ValueSource(strings={
            "chest pain",
            "ChestPain",
            "chest-pain since morning",
            "SEVERE BLEEDING",
            "I can’t breathe",
            "Difficulty In Breathing",
            "Heavy Bleeding from legs",
            "difficulty in breathing",
            "heavy bleeding"
    })
    void emergencySymptoms_inAnySpelling_areEmergency(String symptoms) {
        assertEquals(Priority.EMERGENCY, service.classifyPriority(symptoms));
    }

    @ParameterizedTest
    @ValueSource(strings={
            "HeadAche",
            "Cold",
            "Fever",
            "Sweaty legs and hands",
            "Cough"
    })
    void ordinarySymptoms_areNormal(String symptoms){
        assertEquals(Priority.NORMAL,service.classifyPriority(symptoms));
    }

    @Test
    void noSymptoms_isNormal() {
        assertEquals(Priority.NORMAL, service.classifyPriority(null));
    }

}