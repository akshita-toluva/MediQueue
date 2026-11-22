package com.mediqueue.ai;

import com.mediqueue.entity.FoodAllergy;
import com.mediqueue.entity.MedicalCondition;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DietPromptBuilderTest {

    @Test
    void listsReadableSortedLabels() {
        DietPrompt p = DietPromptBuilder.build(
                Set.of(MedicalCondition.HYPERTENSION, MedicalCondition.CHRONIC_KIDNEY_DISEASE),
                Set.of(FoodAllergy.TREE_NUT));
        assertTrue(p.user().contains("Conditions: chronic kidney disease, hypertension"));
        assertTrue(p.user().contains("Food allergies: tree nut"));
    }

    @Test
    void emptyProfileSaysNone() {
        DietPrompt p = DietPromptBuilder.build(Set.of(), Set.of());
        assertTrue(p.user().contains("Conditions: None"));
        assertTrue(p.user().contains("Food allergies: None"));
    }

    @Test
    void logTextContainsBothParts() {
        DietPrompt p = DietPromptBuilder.build(Set.of(MedicalCondition.DIABETES), Set.of());
        assertTrue(p.asLogText().contains("Use ONLY the health conditions"));
        assertTrue(p.asLogText().contains("diabetes"));
    }
}