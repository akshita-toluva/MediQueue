package com.mediqueue.ai;

public record DietPrompt(String system, String user) {
    // Exactly what is stored in ai_generation_log.prompt_input
    public String asLogText() {
        return system + "\n---\n" + user;
    }
}