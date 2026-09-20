package com.marsline.eip.task2;

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
import java.util.concurrent.ConcurrentHashMap;

/** Test harness + checkpoint for Task 2 (Content-Based Router). */
public final class Task2Runner {

    private Task2Runner() {
    }

    public static TaskResult run() throws Exception {
        List<Booking> bookings = List.of(
                new Booking("BKG-2001", "Raiza Atienza", "Cabuyao", "Manila", "2026-09-12", "01A"),
                new Booking("BKG-2002", "Marrish Asuncion", "Cabuyao", "Makati", "2026-09-12", "02B"),
                new Booking("BKG-2003", "Dwight Ramos", "Cabuyao", "Batangas", "2026-09-13", "03C"),
                new Booking("BKG-2004", "Kai Sotto", "Cabuyao", "Quezon", "2026-09-13", "04A"),
                new Booking("BKG-2005", "Jalen Green", "Cabuyao", "Pampanga", "2026-09-14", "05D"));

        // Expected branch per booking - written by hand from the rule, NOT taken from the router.
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("BKG-2001", "LOCAL");
        expected.put("BKG-2002", "LOCAL");
        expected.put("BKG-2003", "PROVINCIAL");
        expected.put("BKG-2004", "PROVINCIAL");
        expected.put("BKG-2005", "PROVINCIAL");

        // Actual branch = the target system that really received the message.
        Map<String, String> landedIn = new ConcurrentHashMap<>();

        try (EipRuntime rt = EipRuntime.start("marsline-task2")) {
            CamelContext ctx = rt.context();
            ctx.addRoutes(new Task2ContentBasedRouter(
                    ex -> landedIn.put(ex.getIn().getHeader("bookingId", String.class), "LOCAL"),
                    ex -> landedIn.put(ex.getIn().getHeader("bookingId", String.class), "PROVINCIAL")));
            rt.startCamel();

            ProducerTemplate template = ctx.createProducerTemplate();
            for (Booking b : bookings) {
                template.sendBody(Task2ContentBasedRouter.INBOUND, Json.compact(b));
            }

            Waiter.await(() -> landedIn.size() >= bookings.size(), 10_000);

            Checkpoint cp = new Checkpoint(2, "CONTENT-BASED ROUTER")
                    .expected("Every booking lands in the branch that matches the destination rule\n"
                            + "             (Metro Manila -> LOCAL, everything else -> PROVINCIAL); nothing is dropped.");
            int correct = 0;
            int local = 0;
            int provincial = 0;
            for (Booking b : bookings) {
                String want = expected.get(b.bookingId());
                String got = landedIn.get(b.bookingId());
                boolean ok = want.equals(got);
                if (ok) {
                    correct++;
                }
                if ("LOCAL".equals(got)) {
                    local++;
                } else if ("PROVINCIAL".equals(got)) {
                    provincial++;
                }
                cp.detail(b.bookingId() + " destination=" + b.destination() + " expected=" + want
                        + " actual=" + (got == null ? "DROPPED" : got) + " -> " + (ok ? "OK" : "WRONG"));
            }
            int dropped = bookings.size() - landedIn.size();
            boolean pass = correct == bookings.size() && dropped == 0;
            cp.actual(correct + "/" + bookings.size() + " correctly routed, " + dropped + " dropped")
                    .stat("Messages Sent", bookings.size())
                    .stat("LOCAL queue", local)
                    .stat("PROVINCIAL queue", provincial)
                    .stat("Dropped", dropped);
            String status = cp.print(pass);
            return new TaskResult(2, "Content-Based Router", "booking.local / booking.provincial", status,
                    "LOCAL " + local + " / PROVINCIAL " + provincial + " (" + correct + "/" + bookings.size() + " correct)");
        }
    }
}
