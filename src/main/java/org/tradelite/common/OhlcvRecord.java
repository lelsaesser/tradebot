package org.tradelite.common;

import java.time.LocalDate;

public record OhlcvRecord(
        String symbol,
        LocalDate date,
        double open,
        double high,
        double low,
        double close,
        long volume) {}
