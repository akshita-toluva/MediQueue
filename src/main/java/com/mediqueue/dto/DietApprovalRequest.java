package com.mediqueue.dto;

import jakarta.validation.constraints.NotNull;

// The id of the suggestion the doctor READ. If it's no longer current -> 409, never approve unseen text.
public record DietApprovalRequest(@NotNull(message = "generationId is required") Long generationId) {

}