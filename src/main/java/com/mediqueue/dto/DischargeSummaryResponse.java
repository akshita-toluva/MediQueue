package com.mediqueue.dto;

import java.time.LocalDateTime;

public record DischargeSummaryResponse(Long appointmentId, String doctorName, String department,
                                       String diagnosisNotes, String followUpInstructions,
                                       LocalDateTime createdAt, LocalDateTime updatedAt,
                                       Long dietSuggestionId, String dietSuggestion,
                                       boolean dietApproved, String dietDisclaimer)
{

}