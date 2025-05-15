package org.tradelite.common;

import lombok.Getter;

import java.util.List;

@Getter
public enum StockTicker {
    AAPL("AAPL"),
//    MSFT("MSFT"),
//    GOOGL("GOOGL"),
//    AMZN("AMZN"),
    TSLA("TSLA"),
    META("META"),
    NFLX("NFLX"),
    NVDA("NVDA"),
    AMD("AMD"),
    COIN("COIN"),
    MSTR("MSTR"),
    RKLB("RKLB"),
    UBER("UBER"),
    PLTR("PLTR"),
    SPOT("SPOT"),
    HOOD("HOOD");

    private final String ticker;

    StockTicker(String ticker) {
        this.ticker = ticker;
    }

    public static List<StockTicker> getAll() {
        return List.of(StockTicker.values());
    }
}
