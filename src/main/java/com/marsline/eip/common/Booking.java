package com.marsline.eip.common;

/** A MARSLINE booking as produced by the Online Booking System (serialized to JSON by Jackson). */
public record Booking(String bookingId,
                      String customer,
                      String origin,
                      String destination,
                      String travelDate,
                      String seat) {
}
