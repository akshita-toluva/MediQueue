package com.mediqueue.dto;

import com.mediqueue.entity.FoodAllergy;
import com.mediqueue.entity.MedicalCondition;

import java.time.LocalDateTime;
import java.util.Set;

// updatedAt == null means "never filled in". The UI shows that instead of a 404.
public record HealthProfileResponse(
        Set<MedicalCondition> conditions,
        Set<FoodAllergy> foodAllergies,
        LocalDateTime updatedAt
) {}