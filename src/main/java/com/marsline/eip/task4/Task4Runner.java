package com.marsline.eip.task4;

import com.fasterxml.jackson.databind.JsonNode;
import com.marsline.eip.common.Checkpoint;
import com.marsline.eip.common.EipRuntime;
import com.marsline.eip.common.Json;
import com.marsline.eip.common.TaskResult;
import com.marsline.eip.common.Waiter;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Test harness + checkpoint for Task 4 (Message Translator). */
public final class Task4Runner {

    private Task4Runner() {
    }

    private static String ticketXml(String ticketId, String bookingId, String customer, String origin,
                                    String destination, String date, String seat) {
        return "<ticket>\n"
                + "  <ticketId>" + ticketId + "</ticketId>\n"
                + "  <bookingId>" + bookingId + "</bookingId>\n"
                + "  <customerName>" + customer + "</customerName>\n"
                + "  <origin>" + origin + "</origin>\n"
                + "  <destination>" + destination + "</destination>\n"
                + "  <travelDate>" + date + "</travelDate>\n"
                + "  <seatNumber>" + seat + "</seatNumber>\n"
                + "</ticket>";
    }

    public static TaskResult run() throws Exception {
        List<String> tickets = List.of(
                ticketXml("T-4001", "BKG-4001", "Raiza Atienza", "Cabuyao", "Quezon City", "2026-09-12", "12A"),
                ticketXml("T-4002", "BKG-4002", "Marrish Asuncion", "Cabuyao", "Batangas City", "2026-09-13", "07C"),
                ticketXml("T-4003", "BKG-4003", "Dwight Ramos", "Cabuyao", "Lucena", "2026-09-14", "03B"));

        Map<String, JsonNode> crmReceived = new ConcurrentHashMap<>();

        try (EipRuntime rt = EipRuntime.start("marsline-task4")) {
            CamelContext ctx = rt.context();
            ctx.addRoutes(new Task4MessageTranslator(ex -> {
                JsonNode n = Json.tree(ex.getIn().getBody(String.class));
                crmReceived.put(n.path("ticketId").asText(), n);
            }));
            rt.startCamel();

            ProducerTemplate template = ctx.createProducerTemplate();
            for (String xml : tickets) {
                template.sendBody(Task4MessageTranslator.LEGACY_QUEUE, xml);
            }

            Waiter.await(() -> crmReceived.size() >= tickets.size(), 10_000);

            // ---- checkpoint: compare every XML tag name/value against the JSON the CRM really received
            Checkpoint cp = new Checkpoint(4, "MESSAGE TRANSLATOR")
                    .expected("All legacy XML tickets translate to valid JSON accepted by the CRM route,\n"
                            + "             every XML field is preserved (7/7 per ticket, no data loss).");
            int preserved = 0;
            int total = 0;
            int accepted = 0;
            for (String xml : tickets) {
                Map<String, String> xmlFields = LegacyTicketParser.fields(xml);
                String ticketId = xmlFields.get("ticketId");
                JsonNode json = crmReceived.get(ticketId);
                int ticketPreserved = 0;
                for (Map.Entry<String, String> e : xmlFields.entrySet()) {
                    total++;
                    if (json != null && e.getValue().equals(json.path(e.getKey()).asText(null))) {
                        ticketPreserved++;
                        preserved++;
                    }
                }
                if (json != null) {
                    accepted++;
                }
                cp.detail(ticketId + " -> CRM accepted=" + (json != null) + ", fields preserved="
                        + ticketPreserved + "/" + xmlFields.size());
            }

            // XXE hardening self-check: a DOCTYPE payload must be rejected by the parser
            boolean xxeBlocked;
            try {
                LegacyTicketParser.parse("<?xml version=\"1.0\"?><!DOCTYPE ticket [<!ENTITY x SYSTEM \"file:///etc/passwd\">]>"
                        + "<ticket><ticketId>&x;</ticketId></ticket>");
                xxeBlocked = false;
            } catch (Exception expected) {
                xxeBlocked = true;
            }
            cp.detail("XXE guard (DOCTYPE payload) -> rejected=" + xxeBlocked);

            boolean pass = accepted == tickets.size() && preserved == total && total == tickets.size() * 7 && xxeBlocked;
            cp.actual(accepted + "/" + tickets.size() + " tickets accepted by CRM, " + preserved + "/" + total + " fields preserved")
                    .stat("Tickets Sent", tickets.size())
                    .stat("Tickets Accepted by CRM", accepted)
                    .stat("Fields Preserved", preserved + "/" + total)
                    .stat("XXE Guard", xxeBlocked ? "DOCTYPE rejected" : "NOT BLOCKED");
            String status = cp.print(pass);
            return new TaskResult(4, "Message Translator", "Legacy XML -> Modern JSON", status,
                    "Fields preserved (" + preserved + "/" + total + ")");
        }
    }
}
