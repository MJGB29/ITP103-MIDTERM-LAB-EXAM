package com.marsline.eip.task3;

import com.fasterxml.jackson.databind.JsonNode;
import com.marsline.eip.common.Json;
import org.apache.camel.AggregationStrategy;
import org.apache.camel.Exchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Custom AggregationStrategy: folds BOOKING / PAYMENT / TRIP fragments into one BookingSummary. */
public class BookingAggregationStrategy implements AggregationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(BookingAggregationStrategy.class);

    @Override
    public Exchange aggregate(Exchange oldExchange, Exchange newExchange) {
        String bookingId = newExchange.getIn().getHeader("bookingId", String.class);
        String partType = newExchange.getIn().getHeader("partType", String.class);
        JsonNode part = Json.tree(newExchange.getIn().getBody(String.class));

        BookingSummary summary;
        Exchange result;
        if (oldExchange == null) {
            summary = new BookingSummary(bookingId);
            newExchange.getIn().setBody(summary);
            result = newExchange;
            LOG.info("[AGGREGATOR] First fragment for bookingId={} - new aggregation started", bookingId);
        } else {
            summary = oldExchange.getIn().getBody(BookingSummary.class);
            result = oldExchange;
        }
        summary.apply(partType, part);
        LOG.info("[AGGREGATOR] Merged {} part for bookingId={} -> parts so far {}", partType, bookingId,
                summary.getReceivedParts());
        return result;
    }
}
