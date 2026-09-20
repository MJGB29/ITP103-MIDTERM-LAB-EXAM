package com.marsline.eip.task3;

import com.fasterxml.jackson.databind.JsonNode;
import com.marsline.eip.common.Checkpoint;
import com.marsline.eip.common.EipRuntime;
import com.marsline.eip.common.Json;
import com.marsline.eip.common.TaskResult;
import com.marsline.eip.common.Waiter;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test harness + checkpoint for Task 3 (Aggregator). */
public final class Task3Runner {

    private static final Logger LOG = LoggerFactory.getLogger(Task3Runner.class);

    private record Fragment(String bookingId, String partType, String json) {
    }

    private Task3Runner() {
    }

    private static Map<String, Object> obj(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put((String) keyValues[i], keyValues[i + 1]);
        }
        return m;
    }

    private static Fragment booking(String id, String passenger) {
        return new Fragment(id, "BOOKING", Json.compact(obj("bookingId", id, "passengerName", passenger)));
    }

    private static Fragment trip(String id, String route, String date) {
        return new Fragment(id, "TRIP", Json.compact(obj("bookingId", id, "route", route, "travelDate", date)));
    }

    private static Fragment payment(String id, String status, double amount) {
        return new Fragment(id, "PAYMENT", Json.compact(obj("bookingId", id, "paymentStatus", status, "amount", amount)));
    }

    public static TaskResult run() throws Exception {
        // 8 fragments across 3 bookings, deliberately mixed and out of order.
        // BKG-3003's TRIP part is withheld on purpose.
        List<Fragment> fragments = List.of(
                payment("BKG-3001", "PAID", 180.0),
                trip("BKG-3002", "Cabuyao -> Batangas City", "2026-09-13"),
                booking("BKG-3003", "Dwight Ramos"),
                booking("BKG-3001", "Raiza Atienza"),
                payment("BKG-3002", "PAID", 220.0),
                payment("BKG-3003", "PAID", 180.0),
                trip("BKG-3001", "Cabuyao -> Quezon City", "2026-09-12"),
                booking("BKG-3002", "Marrish Asuncion"));

        Map<String, JsonNode> fulfilled = new ConcurrentHashMap<>();
        Map<String, JsonNode> incomplete = new ConcurrentHashMap<>();

        try (EipRuntime rt = EipRuntime.start("marsline-task3")) {
            CamelContext ctx = rt.context();
            ctx.addRoutes(new Task3Aggregator(
                    ex -> {
                        JsonNode n = Json.tree(ex.getIn().getBody(String.class));
                        fulfilled.put(n.path("bookingId").asText(), n);
                    },
                    ex -> {
                        JsonNode n = Json.tree(ex.getIn().getBody(String.class));
                        incomplete.put(n.path("bookingId").asText(), n);
                    }));
            rt.startCamel();

            ProducerTemplate template = ctx.createProducerTemplate();
            for (Fragment f : fragments) {
                LOG.info("[SEND] {} part for {}", f.partType(), f.bookingId());
                Map<String, Object> headers = new LinkedHashMap<>();
                headers.put("bookingId", f.bookingId());
                headers.put("partType", f.partType());
                template.sendBodyAndHeaders(Task3Aggregator.PARTS, f.json(), headers);
                Thread.sleep(100);
            }
            LOG.info("[SEND] All {} fragments sent. Waiting for the {} ms aggregation timeout of BKG-3003 ...",
                    fragments.size(), Task3Aggregator.COMPLETION_TIMEOUT_MS);

            Waiter.await(() -> fulfilled.size() >= 2 && incomplete.size() >= 1, 20_000);
            Thread.sleep(500); // small grace period: prove BKG-3003 does NOT show up in Fulfillment late

            // ---- checkpoint computed from what Fulfillment / the review queue really received
            Map<String, String[]> expectedComplete = new LinkedHashMap<>();
            expectedComplete.put("BKG-3001", new String[] {"Raiza Atienza", "Cabuyao -> Quezon City", "2026-09-12", "PAID", "180.0"});
            expectedComplete.put("BKG-3002", new String[] {"Marrish Asuncion", "Cabuyao -> Batangas City", "2026-09-13", "PAID", "220.0"});

            Checkpoint cp = new Checkpoint(3, "AGGREGATOR")
                    .expected("BKG-3001 and BKG-3002 reach Fulfillment as complete summaries (3/3 parts);\n"
                            + "             BKG-3003 (TRIP withheld) times out INCOMPLETE and never reaches Fulfillment.");
            int completeOk = 0;
            for (Map.Entry<String, String[]> e : expectedComplete.entrySet()) {
                JsonNode n = fulfilled.get(e.getKey());
                String[] w = e.getValue();
                boolean ok = n != null
                        && w[0].equals(n.path("passengerName").asText())
                        && w[1].equals(n.path("route").asText())
                        && w[2].equals(n.path("travelDate").asText())
                        && w[3].equals(n.path("paymentStatus").asText())
                        && Double.parseDouble(w[4]) == n.path("amount").asDouble(-1);
                if (ok) {
                    completeOk++;
                }
                cp.detail(e.getKey() + " -> " + (n == null ? "NOT received by Fulfillment" : "reached Fulfillment")
                        + ", merged fields correct=" + ok);
            }
            boolean incomplete3003 = incomplete.containsKey("BKG-3003");
            boolean leaked3003 = fulfilled.containsKey("BKG-3003");
            cp.detail("BKG-3003 -> incomplete review queue=" + incomplete3003 + ", leaked to Fulfillment=" + leaked3003
                    + ", paymentStatus/amount present=" + (incomplete3003 && incomplete.get("BKG-3003").hasNonNull("paymentStatus")));

            boolean pass = completeOk == 2 && fulfilled.size() == 2 && incomplete3003 && !leaked3003;
            cp.actual(fulfilled.size() + " complete summaries reached Fulfillment (" + completeOk + "/2 correct); "
                    + "BKG-3003 " + (incomplete3003 && !leaked3003 ? "timed out INCOMPLETE and was withheld" : "was NOT handled correctly"))
                    .stat("Fragments Sent", fragments.size())
                    .stat("Complete Summaries", fulfilled.size())
                    .stat("Incomplete (timed out)", incomplete.size());
            String status = cp.print(pass);
            return new TaskResult(3, "Aggregator", "booking.parts -> booking.summaries", status,
                    "COMPLETE " + fulfilled.size() + " / INCOMPLETE " + incomplete.size());
        }
    }
}
