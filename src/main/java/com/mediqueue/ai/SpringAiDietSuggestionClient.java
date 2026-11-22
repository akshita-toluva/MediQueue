package com.mediqueue.ai;

import com.mediqueue.exception.AiUnavailableException;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Spring AI version of the client. The rest of the app only sees DietSuggestionClient,
// so approval, disclaimer, logging and the prompt are unchanged.
@Component
public class SpringAiDietSuggestionClient implements DietSuggestionClient {

    private static final int MAX_OUTPUT_CHARS = 2000;

    private final ChatClient chatClient;
    private final String model;
    private final boolean configured;

    public SpringAiDietSuggestionClient(ChatClient.Builder builder,   // created for us by the Spring AI starter
                                        @Value("${spring.ai.google.genai.chat.model}") String model,
                                        @Value("${spring.ai.google.genai.api-key}") String apiKey) {
        this.chatClient = builder.build();
        this.model = model;
        this.configured = apiKey != null && !apiKey.isBlank() && !"not-set".equals(apiKey);
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public String generate(DietPrompt prompt) {
        if (!configured) {
            throw new AiUnavailableException("GEMINI_API_KEY is not set");
        }
        String text;
        try {
            text = chatClient.prompt()
                    .system(prompt.system())   // the rules
                    .user(prompt.user())       // only the verified condition/allergy labels
                    .call()
                    .content();
        } catch (RuntimeException e) {
            // Network, quota, wrong key, safety block: all become one exception the service logs as FAILED
            throw new AiUnavailableException("Gemini call failed: " + e.getClass().getSimpleName());
        }
        if (text == null || text.isBlank()) {
            throw new AiUnavailableException("Gemini returned an empty answer");
        }
        text = text.trim();
        return text.length() > MAX_OUTPUT_CHARS ? text.substring(0, MAX_OUTPUT_CHARS) : text;
    }
}