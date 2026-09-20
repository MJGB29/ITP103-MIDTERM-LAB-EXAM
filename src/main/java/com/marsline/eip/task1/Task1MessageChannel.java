package com.marsline.eip.task1;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TASK 1 - MESSAGE CHANNEL.
 *
 * The Online Booking System (source) and the Booking Backend (target) never call each other.
 * They only share the JMS queue name {@code marsline.booking.requests}.
 */
public class Task1MessageChannel extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(Task1MessageChannel.class);

    /** In-process entry used by the Online Booking System to hand a booking to the channel. */
    public static final String ENTRY = "direct:online-booking";
    public static final String QUEUE_NAME = "marsline.booking.requests";
    public static final String QUEUE = "jms:queue:" + QUEUE_NAME;

    private final Processor backendObserver;

    public Task1MessageChannel(Processor backendObserver) {
        this.backendObserver = backendObserver;
    }

    @Override
    public void configure() {
        LOG.info("RouteBuilder initialized: Task1MessageChannel (channel = {})", QUEUE_NAME);

        // ---- SOURCE: Online Booking System -> message channel
        from(ENTRY).routeId("marsline-task1-source")
                .process(ex -> {
                    LOG.info("[SOURCE] Online Booking System sending {}", ex.getIn().getHeader("bookingId"));
                    LOG.info("[SOURCE] Payload: {}", ex.getIn().getBody(String.class));
                    LOG.info("[QUEUE] Sending to {}", QUEUE);
                })
                .to(QUEUE)
                .process(ex -> LOG.info("[QUEUE] Accepted by broker: {}", ex.getIn().getHeader("bookingId")));

        // ---- TARGET: message channel -> Booking Backend
        from(QUEUE).routeId("marsline-task1-target")
                .process(ex -> LOG.info("[TARGET] Booking Backend received message from {} (JMSMessageID={})",
                        QUEUE_NAME, ex.getIn().getHeader("JMSMessageID")))
                .process(backendObserver);
    }
}
