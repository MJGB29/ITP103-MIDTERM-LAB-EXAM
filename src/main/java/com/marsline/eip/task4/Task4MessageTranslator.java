package com.marsline.eip.task4;

import com.fasterxml.jackson.databind.JsonNode;
import com.marsline.eip.common.Json;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TASK 4 - MESSAGE TRANSLATOR.
 *
 * The legacy ticketing terminal only exports XML; the modern MARSLINE CRM only accepts JSON.
 * XML is parsed with the JDK DOM parser into a LegacyTicket and Jackson serializes that object to JSON -
 * a genuine format conversion, not string substitution.
 */
public class Task4MessageTranslator extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(Task4MessageTranslator.class);

    public static final String LEGACY_QUEUE = "jms:queue:marsline.ticket.legacy";
    public static final String JSON_QUEUE = "jms:queue:marsline.ticket.json";

    private final Processor crmObserver;

    public Task4MessageTranslator(Processor crmObserver) {
        this.crmObserver = crmObserver;
    }

    @Override
    public void configure() {
        LOG.info("RouteBuilder initialized: Task4MessageTranslator ({} -> {})", LEGACY_QUEUE, JSON_QUEUE);

        // ---- the translator: legacy XML in, modern JSON out
        from(LEGACY_QUEUE).routeId("marsline-task4-translator")
                .process(ex -> {
                    String xml = ex.getIn().getBody(String.class);
                    LOG.info("[TRANSLATOR] Received legacy XML message:\n{}", xml);
                    LOG.info("[TRANSLATOR] Parsing XML fields (DOM parser, DOCTYPE disallowed) ...");
                    LegacyTicket ticket = LegacyTicketParser.parse(xml);
                    LOG.info("[TRANSLATOR] Translating to JSON format ...");
                    String json = Json.compact(ticket);
                    LOG.info("[TRANSLATOR] Modern JSON generated:\n{}", Json.pretty(ticket));
                    ex.getIn().setBody(json);
                    ex.getIn().setHeader("ticketId", ticket.ticketId());
                })
                .to(JSON_QUEUE);

        // ---- the modern CRM: only accepts valid JSON
        from(JSON_QUEUE).routeId("marsline-task4-crm")
                .process(ex -> {
                    JsonNode node = Json.tree(ex.getIn().getBody(String.class)); // throws if it is not valid JSON
                    LOG.info("[CRM] Modern MARSLINE CRM accepted ticket {} ({} fields)",
                            node.path("ticketId").asText(), node.size());
                })
                .process(crmObserver);
    }
}
