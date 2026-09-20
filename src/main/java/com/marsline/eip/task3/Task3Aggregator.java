package com.marsline.eip.task3;

import com.marsline.eip.common.Json;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TASK 3 - AGGREGATOR.
 *
 * Three independent systems (Booking, Payment, Trip Assignment) each publish one fragment of a booking
 * to {@code marsline.booking.parts}. Camel's Aggregator EIP correlates them by the bookingId header.
 * A summary is forwarded to Fulfillment only when all 3 parts are present; otherwise the aggregation
 * times out after 3 seconds and the partial booking goes to the "incomplete" review queue instead.
 */
public class Task3Aggregator extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(Task3Aggregator.class);

    public static final String PARTS = "jms:queue:marsline.booking.parts";
    public static final String SUMMARIES = "jms:queue:marsline.booking.summaries";
    public static final String INCOMPLETE = "jms:queue:marsline.booking.incomplete";
    public static final long COMPLETION_TIMEOUT_MS = 3000;

    private final Processor fulfillmentObserver;
    private final Processor incompleteObserver;

    public Task3Aggregator(Processor fulfillmentObserver, Processor incompleteObserver) {
        this.fulfillmentObserver = fulfillmentObserver;
        this.incompleteObserver = incompleteObserver;
    }

    @Override
    public void configure() {
        LOG.info("RouteBuilder initialized: Task3Aggregator (correlation key = bookingId, timeout = {} ms)",
                COMPLETION_TIMEOUT_MS);

        Predicate allPartsPresent = ex -> {
            BookingSummary s = ex.getIn().getBody(BookingSummary.class);
            return s != null && s.isComplete();
        };
        Predicate timedOut = ex -> "timeout".equals(ex.getProperty(Exchange.AGGREGATED_COMPLETED_BY, String.class));

        from(PARTS).routeId("marsline-aggregator-route")
                .process(ex -> LOG.info("[PARTS] Received {} part for bookingId={}",
                        ex.getIn().getHeader("partType"), ex.getIn().getHeader("bookingId")))
                .aggregate(header("bookingId"), new BookingAggregationStrategy())
                    .completionPredicate(allPartsPresent)
                    .completionTimeout(COMPLETION_TIMEOUT_MS)
                    .completionTimeoutCheckerInterval(250)
                    .process(ex -> {
                        BookingSummary s = ex.getIn().getBody(BookingSummary.class);
                        boolean incomplete = "timeout".equals(ex.getProperty(Exchange.AGGREGATED_COMPLETED_BY, String.class));
                        if (incomplete) {
                            LOG.warn("[AGGREGATOR] Aggregation timeout for bookingId={}. Missing parts: {}",
                                    s.getBookingId(), s.missingParts());
                        } else {
                            LOG.info("[AGGREGATOR] All parts received for bookingId={}. Creating complete booking summary...",
                                    s.getBookingId());
                            LOG.info("[AGGREGATOR] Complete booking summary created:\n{}", Json.pretty(s));
                        }
                        ex.getIn().removeHeader("partType");
                        ex.getIn().setHeader("bookingId", s.getBookingId());
                        ex.getIn().setHeader("summaryStatus", incomplete ? "INCOMPLETE" : "COMPLETE");
                        ex.getIn().setBody(Json.compact(s));
                    })
                    .choice()
                        .when(timedOut)
                            .process(ex -> LOG.warn("[AGGREGATOR] Incomplete booking {} NOT forwarded to Fulfillment. Sending to {}",
                                    ex.getIn().getHeader("bookingId"), INCOMPLETE))
                            .to(INCOMPLETE)
                        .otherwise()
                            .process(ex -> LOG.info("[AGGREGATOR] Forwarding {} to Fulfillment via {}",
                                    ex.getIn().getHeader("bookingId"), SUMMARIES))
                            .to(SUMMARIES)
                    .end()
                .end();

        from(SUMMARIES).routeId("marsline-task3-fulfillment")
                .process(ex -> LOG.info("[FULFILLMENT] Received COMPLETE booking summary for {}",
                        ex.getIn().getHeader("bookingId")))
                .process(fulfillmentObserver);

        from(INCOMPLETE).routeId("marsline-task3-incomplete-review")
                .process(ex -> LOG.warn("[REVIEW] Incomplete booking {} parked for manual review",
                        ex.getIn().getHeader("bookingId")))
                .process(incompleteObserver);
    }
}
