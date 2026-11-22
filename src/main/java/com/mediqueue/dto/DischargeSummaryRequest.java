package com.mediqueue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DischargeSummaryRequest(
        @NotBlank(message = "Diagnosis notes are required")
        @Size(max = 4000, message = "Diagnosis notes must be at most 4000 characters")
        String diagnosisNotes,

        @Size(max = 2000, message = "Follow-up instructions must be at most 2000 characters")
        String followUpInstructions) {
}