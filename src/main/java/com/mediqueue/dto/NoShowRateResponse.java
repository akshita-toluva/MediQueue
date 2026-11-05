package com.mediqueue.dto;


public record NoShowRateResponse(String department, long noShows, long resolved, double rate) {
    // Hibernate calls this 3-arg constructor from the JPQL query.
    // The rate is computed here, so the divide-by-zero guard lives in one place.
    public NoShowRateResponse(String department, Long noShows, Long resolved) {
        this(department, noShows, resolved, resolved == 0 ? 0.0 : (double) noShows / resolved);
    }
}
