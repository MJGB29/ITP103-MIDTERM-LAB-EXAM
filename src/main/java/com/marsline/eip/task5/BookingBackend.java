package com.marsline.eip.task5;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Real, stateful failing-backend simulator. For every bookingId it fails a configured number of
 * attempts (a temporary outage) and then succeeds; a bookingId configured with {@link #ALWAYS} never recovers.
 */
public final class BookingBackend {

    public static final int ALWAYS = Integer.MAX_VALUE;

    private final Map<String, Integer> plannedFailures;
    private final Map<String, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final Map<String, List<String>> history = new ConcurrentHashMap<>();

    public BookingBackend(Map<String, Integer> plannedFailures) {
        this.plannedFailures = Map.copyOf(plannedFailures);
    }

    /** Processes one attempt; throws while the simulated outage lasts. */
    public void handle(String bookingId) {
        int attempt = attempts.computeIfAbsent(bookingId, k -> new AtomicInteger()).incrementAndGet();
        List<String> log = history.computeIfAbsent(bookingId, k -> new CopyOnWriteArrayList<>());
        if (attempt <= plannedFailures.getOrDefault(bookingId, 0)) {
            log.add("FAILED");
            throw new IllegalStateException("Booking Backend temporarily unavailable for " + bookingId
                    + " (attempt " + attempt + ")");
        }
        log.add("SUCCESS");
    }

    public int attempts(String bookingId) {
        AtomicInteger a = attempts.get(bookingId);
        return a == null ? 0 : a.get();
    }

    public List<String> history(String bookingId) {
        return history.getOrDefault(bookingId, List.of());
    }
}
