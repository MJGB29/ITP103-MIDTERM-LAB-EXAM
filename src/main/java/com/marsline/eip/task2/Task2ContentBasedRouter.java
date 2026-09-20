package com.marsline.eip.task2;

import com.fasterxml.jackson.databind.JsonNode;
import com.marsline.eip.common.Json;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * TASK 2 - CONTENT-BASED ROUTER.
 *
 * Reads the "destination" field of every booking. Metro Manila destinations are LOCAL, every other
 * destination is PROVINCIAL. "Quezon" (province) and "Quezon City" (Metro Manila) are different.
 * The otherwise() branch guarantees that no message is ever dropped.
 */
public class Task2ContentBasedRouter extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(Task2ContentBasedRouter.class);

    public static final String INBOUND = "jms:queue:marsline.booking.inbound";
    public static final String LOCAL = "jms:queue:marsline.booking.local";
    public static final String PROVINCIAL = "jms:queue:marsline.booking.provincial";

    /** Metro Manila destinations (normalized: lower case, no accents, single spaces). */
    private static final Set<String> METRO_MANILA = Set.of(
            "manila", "makati", "quezon city", "pasig", "taguig",
            "mandaluyong", "pasay", "paranaque", "caloocan");

    private final Processor localObserver;
    private final Processor provincialObserver;

    public Task2ContentBasedRouter(Processor localObserver, Processor provincialObserver) {
        this.localObserver = localObserver;
        this.provincialObserver = provincialObserver;
    }

    static String normalize(String destination) {
        if (destination == null) {
            return "";
        }
        String s = Normalizer.normalize(destination.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    /** The routing rule itself. */
    public static boolean isLocal(String destination) {
        return METRO_MANILA.contains(normalize(destination));
    }

    @Override
    public void configure() {
        LOG.info("RouteBuilder initialized: Task2ContentBasedRouter. Inbound: {}", INBOUND);

        Predicate isMetroManila = ex -> isLocal(ex.getIn().getHeader("destination", String.class));

        // ---- the router
        from(INBOUND).routeId("marsline-router-task2")
                .process(ex -> {
                    String id = "?";
                    String destination = "";
                    try {
                        JsonNode node = Json.tree(ex.getIn().getBody(String.class));
                        id = node.path("bookingId").asText("?");
                        destination = node.path("destination").asText("");
                    } catch (RuntimeException e) {
                        LOG.warn("[ROUTER] Unreadable booking, sending to PROVINCIAL/review: {}", e.getMessage());
                    }
                    ex.getIn().setHeader("bookingId", id);
                    ex.getIn().setHeader("destination", destination);
                    LOG.info("[ROUTER] Received booking {} - evaluating destination: '{}'", id, destination);
                })
                .choice()
                    .when(isMetroManila)
                        .process(ex -> LOG.info("[ROUTER] {} destination='{}' -> LOCAL queue ({})",
                                ex.getIn().getHeader("bookingId"), ex.getIn().getHeader("destination"), LOCAL))
                        .to(LOCAL)
                    .otherwise()
                        .process(ex -> LOG.info("[ROUTER] {} destination='{}' -> PROVINCIAL queue ({})",
                                ex.getIn().getHeader("bookingId"), ex.getIn().getHeader("destination"), PROVINCIAL))
                        .to(PROVINCIAL)
                .end();

        // ---- the two independent target systems
        from(LOCAL).routeId("marsline-task2-local")
                .process(ex -> LOG.info("[LOCAL] Local / Metro Processing System received {}",
                        ex.getIn().getHeader("bookingId")))
                .process(localObserver);

        from(PROVINCIAL).routeId("marsline-task2-provincial")
                .process(ex -> LOG.info("[PROVINCIAL] Provincial Processing System received {}",
                        ex.getIn().getHeader("bookingId")))
                .process(provincialObserver);
    }
}
