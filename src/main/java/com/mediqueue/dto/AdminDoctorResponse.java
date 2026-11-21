package com.mediqueue.dto;

// Admin view of a doctor. Includes email (the login), which patients never see.
public record AdminDoctorResponse(Long doctorId, String name, String email,
                                  String department, boolean available, int avgConsultationTime) {

}