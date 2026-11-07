package com.mediqueue.dto;

// What a patient needs to choose a doctor, and nothing more.
// No email (= login username), no user id, no availability internals.
public record DoctorSummaryResponse(Long id, String name, String department, int avgConsultationTime) {

}