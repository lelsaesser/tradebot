package org.tradelite.common;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum StockSymbol implements TickerSymbol {
    // do not add special characters to the names, this causes parsing issues with the Telegram
    // messages for some reason
    AAPL("Apple", "AAPL"),
    MSFT("Microsoft", "MSFT"),
    GOOG("Google", "GOOG"),
    AMZN("Amazon", "AMZN"),
    TSLA("Tesla", "TSLA"),
    META("Meta", "META"),
    NFLX("Netflix", "NFLX"),
    NVDA("Nvidia", "NVDA"),
    AMD("AMD", "AMD"),
    COIN("Coinbase", "COIN"),
    MSTR("MicroStrategy", "MSTR"),
    RKLB("Rocket Lab", "RKLB"),
    UBER("Uber", "UBER"),
    PLTR("Palantir", "PLTR"),
    SPOT("Spotify", "SPOT"),
    HOOD("Robinhood", "HOOD"),
    AVGO("Broadcom", "AVGO"),
    AXON("Axon", "AXON"),
    CRWD("CrowdStrike", "CRWD"),
    OKTA("Okta", "OKTA"),
    GLXY("Galaxy Digital", "GLXY"),
    PANW("Palo Alto Networks", "PANW"),
    MP("MP Materials", "MP"),
    APP("AppLovin", "APP"),
    ASML("ASML", "ASML"),
    SMCI("Super Micro Computer", "SMCI"),
    BABA("Alibaba", "BABA"),
    TEM("Tempus AI", "TEM"),
    HIMS("Hims and Hers Health", "HIMS"),
    TSM("Taiwan Semiconductor Manufacturing", "TSM"),
    MU("Micron Technology", "MU"),
    ORCL("Oracle", "ORCL"),
    INTC("Intel", "INTC"),
    DELL("Dell", "DELL"),
    CRWV("CoreWeave", "CRWV"),
    IREN("Iris Energy", "IREN"),
    NBIS("Nebius Group", "NBIS"),
    NET("Cloudflare", "NET");

    private final String companyName;
    private final String ticker;

    StockSymbol(String companyName, String ticker) {
        this.companyName = companyName;
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

    public String getDisplayName() {
        return String.format("%s (%s)", companyName, ticker);
    }

    @Override
    public SymbolType getSymbolType() {
        return SymbolType.STOCK;
    }
}
