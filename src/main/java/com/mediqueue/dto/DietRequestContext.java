package com.mediqueue.dto;

import com.mediqueue.ai.DietPrompt;

// Handed from "prepare" (DB, transaction) to "call AI" (no transaction) to "record" (DB, transaction)
public record DietRequestContext(Long summaryId, DietPrompt prompt) {

}