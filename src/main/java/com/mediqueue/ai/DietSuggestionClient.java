package com.mediqueue.ai;

// Interface = the service never knows it's Gemini. Swappable provider, and easy to mock in tests.
public interface DietSuggestionClient {
    String modelName();
    String generate(DietPrompt prompt);   // throws AiUnavailableException on any failure
}