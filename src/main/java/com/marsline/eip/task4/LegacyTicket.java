package com.marsline.eip.task4;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/** Canonical model of one legacy ticket record (7 fields). Jackson turns it into the CRM JSON. */
@JsonPropertyOrder({"ticketId", "bookingId", "customerName", "origin", "destination", "travelDate", "seatNumber"})
public record LegacyTicket(String ticketId,
                           String bookingId,
                           String customerName,
                           String origin,
                           String destination,
                           String travelDate,
                           String seatNumber) {
}
