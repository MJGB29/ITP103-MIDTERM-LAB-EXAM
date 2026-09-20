package com.marsline.eip.task5;

import com.marsline.eip.common.Json;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * TASK 5 - ERROR HANDLING / RETRY.
 *
 * Uses Camel's own redelivery machinery (onException + maximumRedeliveries + redeliveryDelay), not a
 * hand-written loop. Every failed attempt is audited to {@code marsline.booking.errors}; once the
 * redeliveries are exhausted the booking is moved to {@code marsline.booking.parkinglot} instead of being lost.
 */
public class Task5ErrorChannelRetry extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(Task5ErrorChannelRetry.class);

    public static final String REQUESTS = "jms:queue:marsline.booking.requests.task5";
    public static final String ERRORS = "jms:queue:marsline.booking.errors";
    public static final String PARKINGLOT = "jms:queue:marsline.booking.parkinglot";
    public static final int MAX_REDELIVERIES = 2;
    public static final long REDELIVERY_DELAY_MS = 200;

    private final BookingBackend backend;
    private final Processor deliveredObserver;
    private final Processor parkedObserver;
    private final Processor auditObserver;
    private volatile ProducerTemplate auditTemplate;

    public Task5ErrorChannelRetry(BookingBackend backend, Processor deliveredObserver,
                                  Processor parkedObserver, Processor auditObserver) {
        this.backend = backend;
        this.deliveredObserver = deliveredObserver;
        this.parkedObserver = parkedObserver;
        this.auditObserver = auditObserver;
    }

    private static String bookingId(Exchange ex) {
        try {
            return Json.tree(ex.getIn().getBody(String.class)).path("bookingId").asText("?");
        } catch (RuntimeException e) {
            return "?";
        }
    }

    private synchronized ProducerTemplate auditTemplate(CamelContext context) {
        if (auditTemplate == null) {
            auditTemplate = context.createProducerTemplate();
        }
        return auditTemplate;
    }

    @Override
    public void configure() {
        LOG.info("RouteBuilder initialized: Task5ErrorChannelRetry (maximumRedeliveries={}, redeliveryDelay={} ms)",
                MAX_REDELIVERIES, REDELIVERY_DELAY_MS);

        // ---- Camel's real error handler: retry, then park
        onException(RuntimeException.class)
                .maximumRedeliveries(MAX_REDELIVERIES)
                .redeliveryDelay(REDELIVERY_DELAY_MS)
                .onRedelivery(ex -> {
                    String id = bookingId(ex);
                    Integer counter = ex.getIn().getHeader(Exchange.REDELIVERY_COUNTER, Integer.class);
                    LOG.warn("Retrying message {} (redelivery {}/{})", id,
                            counter != null ? counter : backend.attempts(id), MAX_REDELIVERIES);
                })
                .handled(true)
                .process(ex -> {
                    String id = bookingId(ex);
                    Exception cause = ex.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    LOG.error("Maximum redeliveries ({}) exhausted for {} - last error: {}", MAX_REDELIVERIES, id,
                            cause == null ? "n/a" : cause.getMessage());
                    LOG.info("Sending {} to parking lot: {}", id, PARKINGLOT);
                })
                .to(PARKINGLOT);

        // ---- the booking route with the (simulated) failing backend
        from(REQUESTS).routeId("marsline-task5-route")
                .process(ex -> LOG.info("Received booking message: {}", bookingId(ex)))
                .process(ex -> {
                    String id = bookingId(ex);
                    try {
                        backend.handle(id);
                    } catch (RuntimeException failure) {
                        int attempt = backend.attempts(id);
                        LOG.warn("Backend unavailable for {} (attempt {}): {}", id, attempt, failure.getMessage());
                        Map<String, Object> audit = new LinkedHashMap<>();
                        audit.put("bookingId", id);
                        audit.put("attempt", attempt);
                        audit.put("error", failure.getMessage());
                        audit.put("timestamp", Instant.now().toString());
                        auditTemplate(ex.getContext()).sendBody(ERRORS, Json.compact(audit));
                        throw failure; // hand the failure to Camel's error handler
                    }
                    int attempt = backend.attempts(id);
                    if (attempt > 1) {
                        LOG.info("Backend recovered for {}", id);
                    }
                    LOG.info("Booking {} successfully processed after {} attempt(s)", id, attempt);
                })
                .process(deliveredObserver);

        // ---- audit trail + parking lot consumers
        from(ERRORS).routeId("marsline-task5-error-audit")
                .process(ex -> LOG.info("[ERROR-QUEUE] Audit entry: {}", ex.getIn().getBody(String.class)))
                .process(auditObserver);

        from(PARKINGLOT).routeId("marsline-task5-parkinglot")
                .process(ex -> LOG.warn("[PARKING LOT] Message {} moved to parking lot for manual review", bookingId(ex)))
                .process(parkedObserver);
    }
}
