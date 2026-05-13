package org.tradelite.client.yahoo;

public record YahooPriceQuote(
        String symbol,
        double currentPrice,
        double previousClose,
        double dailyOpen,
        double dailyHigh,
        double dailyLow,
        double changePercent,
        long timestamp) {}
