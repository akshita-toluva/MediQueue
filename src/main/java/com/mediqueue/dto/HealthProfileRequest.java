package com.mediqueue.dto;

import com.mediqueue.entity.FoodAllergy;
import com.mediqueue.entity.MedicalCondition;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

// PUT = replace the whole profile. An empty set means "none", while a missing field is rejected (400),
// so a buggy client can't wipe allergies by accident.
// Set<@NotNull ...> rejects ["DIABETES", null], which would otherwise reach the DB and cause a 500.
public record HealthProfileRequest(
        @NotNull(message = "conditions is required (use [] for none)")
        Set<@NotNull MedicalCondition> conditions,
        @NotNull(message = "foodAllergies is required (use [] for none)")
        Set<@NotNull FoodAllergy> foodAllergies
) {}