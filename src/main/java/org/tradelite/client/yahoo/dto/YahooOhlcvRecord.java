package org.tradelite.client.yahoo.dto;

import java.time.LocalDate;

public record YahooOhlcvRecord(
        String symbol,
        LocalDate date,
        double open,
        double high,
        double low,
        double close,
        double adjClose,
        long volume) {}
