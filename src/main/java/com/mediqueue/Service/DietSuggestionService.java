package com.mediqueue.Service;

import com.mediqueue.ai.DietSuggestionClient;
import com.mediqueue.dto.DietRequestContext;
import com.mediqueue.dto.DischargeSummaryResponse;
import com.mediqueue.entity.User;
import com.mediqueue.exception.AiUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// NO @Transactional on purpose. The AI call takes seconds; holding a DB connection or the
// doctor-row lock that long would stall booking and call-next for this doctor.
// prepare (tx) -> AI call (no tx) -> record (tx). Calls go through another bean, so the proxies apply.
@Service
@RequiredArgsConstructor
public class DietSuggestionService {

    private final DischargeSummaryService dischargeSummaryService;
    private final DietSuggestionClient client;

    public DischargeSummaryResponse generate(User doctorUser, Long appointmentId) {
        DietRequestContext ctx = dischargeSummaryService.prepareDietPrompt(doctorUser, appointmentId);
        long start = System.nanoTime();
        try {
            String output = client.generate(ctx.prompt());
            return dischargeSummaryService.recordDietResult(doctorUser, ctx, client.modelName(),
                    output, null, elapsedMs(start));
        } catch (AiUnavailableException e) {
            // Failures are logged too: "the AI failed 3 times today" is an auditable fact
            dischargeSummaryService.recordDietResult(doctorUser, ctx, client.modelName(),
                    null, e.getMessage(), elapsedMs(start));
            throw e;
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}