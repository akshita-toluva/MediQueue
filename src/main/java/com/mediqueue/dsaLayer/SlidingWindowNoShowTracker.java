package com.mediqueue.dsaLayer;

import org.springframework.security.core.parameters.P;

import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/*
 * Per-doctor 30-day rolling no-show tracker.
 * Deque holds events in arrival order; oldest sits at the front.
 * Insert appends to the back, eviction pops expired events off the front -
 * each event is touched exactly twice (add, evict)
 * so this is O(1) per call instead of re-scanning history from the DB every time
*/
public class SlidingWindowNoShowTracker {

    private static final int WINDOW_DAYS=30;

    private static class Event{
        final LocalDate date;
        final boolean noShow;
        Event(LocalDate date,boolean noShow)
        {
            this.date=date;
            this.noShow=noShow;
        }
    }

    private final Map<Long, Deque<Event>> windows=new HashMap<>();
    private final Map<Long, Integer> noShowCounts=new HashMap<>();

    public synchronized void recordOutcome(Long doctorId,LocalDate date,boolean noShow)
    {
        Deque<Event> window=windows.computeIfAbsent(doctorId,k->new ArrayDeque<>());
        window.addLast(new Event(date, noShow));
        if(noShow)
        {
            noShowCounts.merge(doctorId,1,Integer::sum);
        }
        evictExpired(doctorId,date);

    }

    private void evictExpired(Long doctorId,LocalDate today)
    {
        Deque<Event> window=windows.get(doctorId);
        LocalDate cutoff=today.minusDays(WINDOW_DAYS);
        while(!window.isEmpty() && window.peekFirst().date.isBefore(cutoff))
        {
            Event expired=window.pollFirst();
            if(expired.noShow)
            {
                noShowCounts.merge(doctorId,-1,Integer::sum);
            }
        }
    }

    public synchronized double getNoShowRate(Long doctorId)
    {
        Deque<Event> window= windows.get(doctorId);
        if(window==null || window.isEmpty())
        {
            return 0.0;
        }
        evictExpired(doctorId,LocalDate.now());
        if(window.isEmpty())
        {
            return 0.0;
        }
        int noShows=noShowCounts.getOrDefault(doctorId,0);
        return (double)noShows/ window.size();
    }

    public synchronized int getSampleSize(Long doctorId)
    {
        Deque<Event> window=windows.get(doctorId);
        return window == null ? 0 : window.size();
    }
}
