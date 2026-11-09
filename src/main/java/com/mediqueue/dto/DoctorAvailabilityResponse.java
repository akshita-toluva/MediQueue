package com.mediqueue.dto;

// What a doctor/admin needs back after toggling availability. Nothing from the linked User (email, role, createdAt).
public record DoctorAvailabilityResponse(Long id, String name, String department, boolean available) {}