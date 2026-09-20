package com.marsline.eip.task3;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Set;
import java.util.TreeSet;

/**
 * The merged booking: BOOKING part (passenger) + TRIP part (route, date) + PAYMENT part (status, amount).
 * Only the business fields are serialized to JSON; the bookkeeping helpers are ignored.
 */
@JsonPropertyOrder({"bookingId", "passengerName", "route", "travelDate", "paymentStatus", "amount"})
public class BookingSummary {

    public static final Set<String> REQUIRED_PARTS = Set.of("BOOKING", "PAYMENT", "TRIP");

    private final String bookingId;
    private String passengerName;
    private String route;
    private String travelDate;
    private String paymentStatus;
    private Double amount;
    private final Set<String> receivedParts = new TreeSet<>();

    public BookingSummary(String bookingId) {
        this.bookingId = bookingId;
    }

    /** Merges one fragment into this summary. */
    public void apply(String partType, JsonNode part) {
        switch (partType) {
            case "BOOKING" -> passengerName = part.path("passengerName").asText(null);
            case "TRIP" -> {
                route = part.path("route").asText(null);
                travelDate = part.path("travelDate").asText(null);
            }
            case "PAYMENT" -> {
                paymentStatus = part.path("paymentStatus").asText(null);
                amount = part.path("amount").isNumber() ? part.path("amount").asDouble() : null;
            }
            default -> throw new IllegalArgumentException("Unknown part type: " + partType);
        }
        receivedParts.add(partType);
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getPassengerName() {
        return passengerName;
    }

    public String getRoute() {
        return route;
    }

    public String getTravelDate() {
        return travelDate;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public Double getAmount() {
        return amount;
    }

    @JsonIgnore
    public Set<String> getReceivedParts() {
        return receivedParts;
    }

    @JsonIgnore
    public boolean isComplete() {
        return receivedParts.containsAll(REQUIRED_PARTS);
    }

    public Set<String> missingParts() {
        Set<String> missing = new TreeSet<>(REQUIRED_PARTS);
        missing.removeAll(receivedParts);
        return missing;
    }
}
