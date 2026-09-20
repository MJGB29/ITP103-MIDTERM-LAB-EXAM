package com.marsline.eip.task5;

import com.marsline.eip.common.Booking;
import com.marsline.eip.common.Checkpoint;
import com.marsline.eip.common.EipRuntime;
import com.marsline.eip.common.Json;
import com.marsline.eip.common.TaskResult;
import com.marsline.eip.common.Waiter;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Test harness + checkpoint for Task 5 (Error Handling / Retry). */
public final class Task5Runner {

    private Task5Runner() {
    }

    public static TaskResult run() throws Exception {
        List<Booking> bookings = List.of(
                new Booking("BKG-5001", "Raiza Atienza", "Cabuyao", "Quezon City", "2026-09-13", "01A"),
                new Booking("BKG-5002", "Marrish Asuncion", "Cabuyao", "Batangas City", "2026-09-13", "02B"),
                new Booking("BKG-5003", "Dwight Ramos", "Cabuyao", "Lucena", "2026-09-14", "03C"));

        Map<String, Integer> failures = new LinkedHashMap<>();
        failures.put("BKG-5001", 2);                    // fails twice, succeeds on the 3rd attempt
        failures.put("BKG-5002", 1);                    // fails once, succeeds on the 2nd attempt
        failures.put("BKG-5003", BookingBackend.ALWAYS); // fails on every attempt
        BookingBackend backend = new BookingBackend(failures);

        Set<String> delivered = ConcurrentHashMap.newKeySet();
        Set<String> parked = ConcurrentHashMap.newKeySet();
        Map<String, AtomicInteger> auditCount = new ConcurrentHashMap<>();
        AtomicInteger auditTotal = new AtomicInteger();

        try (EipRuntime rt = EipRuntime.start("marsline-task5")) {
            CamelContext ctx = rt.context();
            ctx.addRoutes(new Task5ErrorChannelRetry(backend,
                    ex -> delivered.add(Json.tree(ex.getIn().getBody(String.class)).path("bookingId").asText()),
                    ex -> parked.add(Json.tree(ex.getIn().getBody(String.class)).path("bookingId").asText()),
                    ex -> {
                        String id = Json.tree(ex.getIn().getBody(String.class)).path("bookingId").asText();
                        auditCount.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
                        auditTotal.incrementAndGet();
                    }));
            rt.startCamel();

            ProducerTemplate template = ctx.createProducerTemplate();
            for (Booking b : bookings) {
                template.sendBody(Task5ErrorChannelRetry.REQUESTS, Json.compact(b));
            }

            // 2 failures + 1 failure + 3 failures = 6 audit entries expected
            Waiter.await(() -> delivered.size() + parked.size() >= bookings.size() && auditTotal.get() >= 6, 20_000);

            // ---- checkpoint computed from the backend's real attempt history and the queues' real contents
            Map<String, String> expectedOutcome = new LinkedHashMap<>();
            expectedOutcome.put("BKG-5001", "DELIVERED");
            expectedOutcome.put("BKG-5002", "DELIVERED");
            expectedOutcome.put("BKG-5003", "PARKED");
            Map<String, Integer> expectedAttempts = Map.of("BKG-5001", 3, "BKG-5002", 2, "BKG-5003", 1 + Task5ErrorChannelRetry.MAX_REDELIVERIES);

            Checkpoint cp = new Checkpoint(5, "ERROR HANDLING / RETRY")
                    .expected("BKG-5001 and BKG-5002 recover via Camel redelivery and are delivered;\n"
                            + "             BKG-5003 is moved to marsline.booking.parkinglot after 2 retries; 0 messages lost.");
            int correct = 0;
            for (Booking b : bookings) {
                String id = b.bookingId();
                String outcome = delivered.contains(id) ? "DELIVERED" : parked.contains(id) ? "PARKED" : "LOST";
                int attempts = backend.attempts(id);
                boolean ok = expectedOutcome.get(id).equals(outcome) && expectedAttempts.get(id) == attempts;
                if (ok) {
                    correct++;
                }
                cp.detail(id + " -> " + String.join(" -> ", backend.history(id)) + " | attempts=" + attempts
                        + " | outcome=" + outcome + " | audit entries="
                        + (auditCount.containsKey(id) ? auditCount.get(id).get() : 0) + " -> " + (ok ? "OK" : "UNEXPECTED"));
            }
            int lost = bookings.size() - delivered.size() - parked.size();
            boolean pass = correct == bookings.size() && lost == 0 && auditTotal.get() == 6;
            cp.actual(delivered.size() + " delivered after retry, " + parked.size() + " parked, " + lost + " lost")
                    .stat("Messages Sent", bookings.size())
                    .stat("Delivered (recovered)", delivered.size())
                    .stat("Parked (exhausted)", parked.size())
                    .stat("Audit entries (errors)", auditTotal.get())
                    .stat("Messages Lost", lost);
            String status = cp.print(pass);
            return new TaskResult(5, "Error Handling + Retry", "booking.errors -> parkinglot", status,
                    "Recovered " + delivered.size() + " / Parked " + parked.size());
        }
    }
}
