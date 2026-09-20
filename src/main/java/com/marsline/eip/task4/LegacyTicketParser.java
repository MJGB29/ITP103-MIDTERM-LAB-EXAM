package com.marsline.eip.task4;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Real DOM XML parsing (javax.xml.parsers.DocumentBuilder) - no regex. Hardened against XXE:
 * DOCTYPE declarations are rejected outright and external entities are never resolved.
 */
public final class LegacyTicketParser {

    /** The 7 fields the legacy ticketing terminal exports, in export order. */
    public static final List<String> FIELDS = List.of(
            "ticketId", "bookingId", "customerName", "origin", "destination", "travelDate", "seatNumber");

    private LegacyTicketParser() {
    }

    private static Element rootElement(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new DefaultHandler()); // keep the JDK from printing "[Fatal Error]" to stderr
        Document document = builder.parse(new InputSource(new StringReader(xml)));
        return document.getDocumentElement();
    }

    /** Every child element of the root as tagName -> text (generic, independent of the 7-field model). */
    public static Map<String, String> fields(String xml) throws Exception {
        Element root = rootElement(xml);
        Map<String, String> result = new LinkedHashMap<>();
        for (Node n = root.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                result.put(n.getNodeName(), n.getTextContent().trim());
            }
        }
        return result;
    }

    /** Parses the XML field by field into a LegacyTicket. Missing fields are an error. */
    public static LegacyTicket parse(String xml) throws Exception {
        Element root = rootElement(xml);
        if (!"ticket".equals(root.getNodeName())) {
            throw new IllegalArgumentException("Unexpected root element <" + root.getNodeName() + ">, expected <ticket>");
        }
        Map<String, String> f = fields(xml);
        for (String required : FIELDS) {
            if (!f.containsKey(required)) {
                throw new IllegalArgumentException("Legacy ticket is missing <" + required + ">");
            }
        }
        return new LegacyTicket(
                f.get("ticketId"), f.get("bookingId"), f.get("customerName"), f.get("origin"),
                f.get("destination"), f.get("travelDate"), f.get("seatNumber"));
    }
}
