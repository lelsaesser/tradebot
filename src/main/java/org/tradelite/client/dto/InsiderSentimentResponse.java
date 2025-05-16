package org.tradelite.client.dto;

import java.util.List;


public record InsiderSentimentResponse (
        List<InsiderSentiment> data,
        String symbol
) {
    public record InsiderSentiment(
            int change,
            int month,
            int year,
            String symbol,
            double mspr
    ) {}
}
