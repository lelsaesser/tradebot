package org.tradelite.common;

import lombok.Getter;

import java.util.List;

@Getter
public enum TickerSymbol {
    AAPL("AAPL"),
//    MSFT("MSFT"),
    GOOG("GOOG"),
    AMZN("AMZN"),
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
    HOOD("HOOD"),
    AVGO("AVGO"),
    AXON("AXON"),
    CRWD("CRWD"),
    OKTA("OKTA"),
    NET("NET");

    private final String ticker;

    TickerSymbol(String ticker) {
        this.ticker = ticker;
    }

    public static List<TickerSymbol> getAll() {
        return List.of(TickerSymbol.values());
    }
}
