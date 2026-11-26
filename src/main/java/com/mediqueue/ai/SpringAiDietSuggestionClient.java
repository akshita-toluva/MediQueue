package com.mediqueue.ai;

import com.mediqueue.exception.AiUnavailableException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
public class SpringAiDietSuggestionClient implements DietSuggestionClient {

    private static final int MAX_OUTPUT_CHARS = 2000;
    private static final int MAX_ERROR_CHARS = 300;

    private final ChatClient chatClient;
    private final String model;
    private final String apiKey;
    private final boolean configured;
    private final int timeoutSeconds;
    // One virtual thread per call (Java 21): cheap, and lets us STOP WAITING after the time limit
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public SpringAiDietSuggestionClient(ChatClient.Builder builder,
                                        @Value("${spring.ai.google.genai.chat.model}") String model,
                                        @Value("${spring.ai.google.genai.api-key}") String apiKey,
                                        @Value("${mediqueue.ai.timeout-seconds:20}") int timeoutSeconds) {
        this.chatClient = builder.build();
        this.model = model;
        this.apiKey = apiKey;
        this.configured = apiKey != null && !apiKey.isBlank() && !"not-set".equals(apiKey);
        this.timeoutSeconds = timeoutSeconds;
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
        Future<String> call = executor.submit(() -> chatClient.prompt()
                .system(prompt.system())   // the rules
                .user(prompt.user())       // only verified condition/allergy labels
                .call()
                .content());
        String text;
        try {
            text = call.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            call.cancel(true);
            log.warn("Gemini call timed out after {}s", timeoutSeconds);
            throw new AiUnavailableException("Timed out after " + timeoutSeconds + "s");
        } catch (ExecutionException e) {
            Throwable cause = (e.getCause() != null) ? e.getCause() : e;
            log.warn("Gemini call failed", cause);   // full detail: server log only
            throw new AiUnavailableException(safeReason(cause));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiUnavailableException("Interrupted");
        }
        if (text == null || text.isBlank()) {
            throw new AiUnavailableException("Gemini returned an empty answer");
        }
        text = text.trim();
        return text.length() > MAX_OUTPUT_CHARS ? text.substring(0, MAX_OUTPUT_CHARS) : text;
    }

    // Short reason for ai_generation_log: class + first line of the message, key masked, length capped
    private String safeReason(Throwable t) {
        String msg = (t.getMessage() == null) ? "" : t.getMessage().split("\\R", 2)[0];
        if (configured) {
            msg = msg.replace(apiKey, "***");
        }
        String reason = t.getClass().getSimpleName() + (msg.isBlank() ? "" : ": " + msg);
        return reason.length() > MAX_ERROR_CHARS ? reason.substring(0, MAX_ERROR_CHARS) : reason;
    }

    @PreDestroy
    void shutdown() {
        executor.close();
    }
}