package com.mediqueue.Service;

import com.mediqueue.dto.AccuracyStats;
import com.mediqueue.dto.ErrorBucket;
import com.mediqueue.dto.WaitSample;

import java.util.List;

// error = actual - estimated. Positive: patient waited LONGER than told.
public final class WaitTimeAccuracyCalculator {

    public static final int TOLERANCE_MINUTES = 10;

    private WaitTimeAccuracyCalculator() { }

    public static AccuracyStats summarize(String label, List<WaitSample> samples) {
        int n = samples.size();
        if (n == 0) {
            return new AccuracyStats(label, 0, null, null, null);
        }
        int within = 0;
        long absSum = 0;
        long signedSum = 0;
        for (WaitSample s : samples) {
            int error = s.actual() - s.estimated();
            if (Math.abs(error) <= TOLERANCE_MINUTES) within++;
            absSum += Math.abs(error);
            signedSum += error;
        }
        return new AccuracyStats(label, n,
                round1(100.0 * within / n),
                round1((double) absSum / n),
                round1((double) signedSum / n));
    }

    // Always 5 buckets in a fixed order, so the chart never changes shape
    public static List<ErrorBucket> distribution(List<WaitSample> samples) {
        long[] c = new long[5];
        for (WaitSample s : samples) {
            int e = s.actual() - s.estimated();
            if (e < -15) c[0]++;
            else if (e < -5) c[1]++;
            else if (e <= 5) c[2]++;
            else if (e <= 15) c[3]++;
            else c[4]++;
        }
        return List.of(
                new ErrorBucket("15+ min less", c[0]),
                new ErrorBucket("5-15 min less", c[1]),
                new ErrorBucket("Within 5 min", c[2]),
                new ErrorBucket("5-15 min more", c[3]),
                new ErrorBucket("15+ min more", c[4]));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}