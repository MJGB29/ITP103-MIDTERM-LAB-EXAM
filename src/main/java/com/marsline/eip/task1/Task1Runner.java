package com.marsline.eip.task1;

import com.marsline.eip.common.Booking;
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

/** Test harness + checkpoint for Task 1 (Message Channel). */
public final class Task1Runner {

    private static final Logger LOG = LoggerFactory.getLogger(Task1Runner.class);

    private record Received(String payload, String jmsMessageId) {
    }

    private Task1Runner() {
    }

    public static TaskResult run() throws Exception {
        List<Booking> bookings = List.of(
                new Booking("BKG-1001", "Juan Dela Cruz", "Cabuyao", "Manila", "2026-09-15", "12A"),
                new Booking("BKG-1002", "Maria Santos", "Cabuyao", "Batangas", "2026-09-15", "07C"),
                new Booking("BKG-1003", "Pedro Reyes", "Cabuyao", "Quezon", "2026-09-16", "03B"));

        Map<String, String> sent = new LinkedHashMap<>();
        Map<String, Received> received = new ConcurrentHashMap<>();

        try (EipRuntime rt = EipRuntime.start("marsline-task1")) {
            CamelContext ctx = rt.context();
            ctx.addRoutes(new Task1MessageChannel(ex -> {
                String body = ex.getIn().getBody(String.class);
                String id = Json.tree(body).path("bookingId").asText();
                received.put(id, new Received(body, ex.getIn().getHeader("JMSMessageID", String.class)));
                LOG.info("[TARGET] Booking Backend received {}", id);
            }));
            rt.startCamel();

            ProducerTemplate template = ctx.createProducerTemplate();
            for (Booking b : bookings) {
                String json = Json.compact(b);
                sent.put(b.bookingId(), json);
                LOG.info("[SOURCE] Online Booking System sending {} ({} -> {})", b.bookingId(), b.origin(), b.destination());
                template.sendBodyAndHeader(Task1MessageChannel.ENTRY, json, "bookingId", b.bookingId());
            }

            Waiter.await(() -> received.size() >= bookings.size(), 10_000);

            // ---- checkpoint computed from what the backend really received
            Checkpoint cp = new Checkpoint(1, "MESSAGE CHANNEL")
                    .expected("All booking messages travel through the JMS message channel\n"
                            + "             '" + Task1MessageChannel.QUEUE_NAME + "' and are received intact by the Booking Backend.");
            int delivered = 0;
            boolean allIntact = true;
            for (Map.Entry<String, String> e : sent.entrySet()) {
                Received r = received.get(e.getKey());
                boolean isDelivered = r != null;
                boolean intact = isDelivered && e.getValue().equals(r.payload());
                boolean hasBrokerId = isDelivered && r.jmsMessageId() != null && r.jmsMessageId().startsWith("ID:");
                if (isDelivered) {
                    delivered++;
                }
                allIntact &= intact && hasBrokerId;
                cp.detail(e.getKey() + " -> delivered=" + isDelivered + ", payloadIntact=" + intact
                        + ", JMSMessageID=" + (isDelivered ? r.jmsMessageId() : "n/a"));
            }
            boolean pass = delivered == sent.size() && allIntact;
            cp.actual(delivered + " of " + sent.size() + " messages received, "
                    + (allIntact ? "all payloads intact" : "payload/ID check FAILED"))
                    .stat("Messages Sent", sent.size())
                    .stat("Messages Received", delivered);
            String status = cp.print(pass);
            return new TaskResult(1, "Message Channel", Task1MessageChannel.QUEUE_NAME, status,
                    (pass ? "Delivered " : "Delivered only ") + delivered + "/" + sent.size());
        }
    }
}
