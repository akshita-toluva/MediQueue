package com.mediqueue.Service;

import com.mediqueue.dto.DietRequestContext;
import com.mediqueue.entity.User;
import com.mediqueue.exception.AiUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.mediqueue.ai.DietPrompt;
import com.mediqueue.ai.DietSuggestionClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DietSuggestionServiceTest {

    @Mock DischargeSummaryService dischargeSummaryService;
    @Mock DietSuggestionClient client;
    @InjectMocks DietSuggestionService service;

    private final User doctor = User.builder().id(10L).build();
    private final DietRequestContext ctx = new DietRequestContext(5L, new DietPrompt("sys", "user"));

    @Test
    void successPassesOutputToRecord() {
        when(dischargeSummaryService.prepareDietPrompt(doctor, 100L)).thenReturn(ctx);
        when(client.generate(ctx.prompt())).thenReturn("- Drink water");
        when(client.modelName()).thenReturn("test-model");

        service.generate(doctor, 100L);

        verify(dischargeSummaryService).recordDietResult(eq(doctor), eq(ctx), eq("test-model"),
                eq("- Drink water"), isNull(), anyLong());
    }

    @Test
    void failureIsLoggedThenRethrown() {
        when(dischargeSummaryService.prepareDietPrompt(doctor, 100L)).thenReturn(ctx);
        when(client.generate(ctx.prompt())).thenThrow(new AiUnavailableException("Gemini HTTP 429"));
        when(client.modelName()).thenReturn("test-model");

        assertThrows(AiUnavailableException.class, () -> service.generate(doctor, 100L));

        verify(dischargeSummaryService).recordDietResult(eq(doctor), eq(ctx), eq("test-model"),
                isNull(), eq("Gemini HTTP 429"), anyLong());
    }
}