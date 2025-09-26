package org.tradelite.common;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Getter
public enum StockSymbol implements TickerSymbol {
    AAPL("AAPL"),
    MSFT("MSFT"),
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
    GLXY("GLXY"),
    PANW("PANW"),
    MP("MP"),
    APP("APP"),
    ASML("ASML"),
//    COST("COST"),
//    WMT("WMT"),
    SMCI("SMCI"),
    UNH("UNH"),
    BABA("BABA"),
    TEM("TEM"),
    OSCR("OSCR"),
    HIMS("HIMS"),
    TSM("TSM"),
    MU("MU"),
    ORCL("ORCL"),
    INTC("INTC"),
    PONY("PONY"),
    DELL("DELL"),
    CRWV("CRWV"),
    IREN("IREN"),
    NET("NET");

    private final String ticker;

    StockSymbol(String ticker) {
        this.ticker = ticker;
    }

    public static List<StockSymbol> getAll() {
        return List.of(StockSymbol.values());
    }

    public static Optional<StockSymbol> fromString(String input) {
        return Arrays.stream(StockSymbol.values())
                .filter(symbol -> symbol.getTicker().equalsIgnoreCase(input))
                .findFirst();
    }

    @Override
    public String getName() {
        return ticker;
    }

    @Override
    public SymbolType getSymbolType() {
        return SymbolType.STOCK;
    }
}
