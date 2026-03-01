package org.tradelite.repository;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PriceQuoteEntity {

    private final Long id;
    private final String symbol;
    private final long timestamp;
    private final double currentPrice;
    private final double dailyOpen;
    private final double dailyHigh;
    private final double dailyLow;
    private final double changeAmount;
    private final double changePercent;
    private final double previousClose;
}
