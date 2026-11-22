package com.mediqueue.ai;

import com.mediqueue.entity.FoodAllergy;
import com.mediqueue.entity.MedicalCondition;

import java.util.Set;
import java.util.stream.Collectors;

// Deterministic: same profile -> same prompt. Only enum labels go in: no name, no notes, no free text.
public final class DietPromptBuilder {

    static final String SYSTEM = """
            You write short, general diet guidance for a patient leaving a hospital outpatient visit.
            Rules:
            - Use ONLY the health conditions and food allergies listed. Do not assume or mention any other condition, diagnosis or medication.
            - Never recommend a food that contains a listed allergen.
            - Respect the usual diet limits of each listed condition (for example chronic kidney disease: moderate protein, salt, potassium and phosphorus; diabetes: limit added sugar).
            - If no conditions are listed, give general healthy-eating guidance only.
            - No calorie targets, weight-loss plans, supplements or medication advice.
            - Plain text: 4 to 6 lines, each starting with "- ", under 120 words in total. No headings.
            """;

    private DietPromptBuilder() { }

    public static DietPrompt build(Set<MedicalCondition> conditions, Set<FoodAllergy> allergies) {
        String user = "Patient's doctor-verified health profile:\n"
                + "Conditions: " + readable(conditions) + "\n"
                + "Food allergies: " + readable(allergies) + "\n"
                + "Write general diet suggestions for the next two weeks.";
        return new DietPrompt(SYSTEM, user);
    }

    // CHRONIC_KIDNEY_DISEASE -> "chronic kidney disease"; sorted so the prompt is stable
    private static String readable(Set<? extends Enum<?>> values) {
        if (values == null || values.isEmpty()) return "None";
        return values.stream()
                .map(v -> v.name().toLowerCase().replace('_', ' '))
                .sorted()
                .collect(Collectors.joining(", "));
    }
}