package com.mediqueue.Service;

import com.mediqueue.dto.AccuracyStats;
import com.mediqueue.dto.ErrorBucket;
import com.mediqueue.dto.WaitSample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaitTimeAccuracyCalculatorTest {

    private static WaitSample s(int estimated, int actual) {
        return new WaitSample("Cardiology", estimated, actual);
    }

    @Test
    void noVisitsMeansNoDataNotZero() {
        AccuracyStats st = WaitTimeAccuracyCalculator.summarize("All", List.of());
        assertEquals(0, st.visits());
        assertNull(st.withinTolerancePct());
        assertNull(st.meanAbsErrorMin());
    }

    @Test
    void withinToleranceCountsBothDirectionsAndTheEdge() {
        // errors: +10 (edge, counts), -10 (edge, counts), +11 (miss), 0 (hit)
        AccuracyStats st = WaitTimeAccuracyCalculator.summarize("All",
                List.of(s(20, 30), s(30, 20), s(20, 31), s(15, 15)));
        assertEquals(4, st.visits());
        assertEquals(75.0, st.withinTolerancePct());
    }

    @Test
    void biasShowsDirectionAndMeanErrorShowsSize() {
        // errors: +10 and -10 -> they cancel in bias, but the average miss is 10
        AccuracyStats st = WaitTimeAccuracyCalculator.summarize("All", List.of(s(20, 30), s(30, 20)));
        assertEquals(0.0, st.biasMin());
        assertEquals(10.0, st.meanAbsErrorMin());
    }

    @Test
    void bucketBoundaries() {
        // errors: -16, -15, -5, 5, 15, 16
        List<ErrorBucket> b = WaitTimeAccuracyCalculator.distribution(List.of(
                s(30, 14), s(30, 15), s(30, 25), s(30, 35), s(30, 45), s(30, 46)));
        assertEquals(1, b.get(0).count());   // -16           -> 15+ less
        assertEquals(1, b.get(1).count());   // -15           -> 5-15 less
        assertEquals(2, b.get(2).count());   // -5, +5        -> within 5
        assertEquals(1, b.get(3).count());   // +15           -> 5-15 more
        assertEquals(1, b.get(4).count());   // +16           -> 15+ more
    }
}