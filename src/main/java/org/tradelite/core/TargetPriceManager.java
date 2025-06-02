package org.tradelite.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TickerSymbol;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Getter
public class TargetPriceManager {

    private final Map<String, Date> ignoredSymbols = new HashMap<>();

    private final List<TargetPrice> targetPrices = List.of(
            new TargetPrice(StockSymbol.AAPL.getTicker(), 195.0, 0),
            new TargetPrice(StockSymbol.AMD.getTicker(), 90.0, 170.0),
            new TargetPrice(StockSymbol.META.getTicker(), 556.0, 718.0),
            new TargetPrice(StockSymbol.NFLX.getTicker(), 1130, 0),
            new TargetPrice(StockSymbol.NVDA.getTicker(), 120, 0),
            new TargetPrice(StockSymbol.TSLA.getTicker(), 280, 0),
            new TargetPrice(StockSymbol.COIN.getTicker(), 216, 0),
            new TargetPrice(StockSymbol.MSTR.getTicker(), 300, 0),
            new TargetPrice(StockSymbol.RKLB.getTicker(), 26, 0),
            new TargetPrice(StockSymbol.UBER.getTicker(), 83, 0),
            new TargetPrice(StockSymbol.PLTR.getTicker(), 115, 0),
            new TargetPrice(StockSymbol.SPOT.getTicker(), 631, 0),
            new TargetPrice(StockSymbol.HOOD.getTicker(), 52, 0),
            new TargetPrice(StockSymbol.NET.getTicker(), 127, 0),
            new TargetPrice(StockSymbol.AVGO.getTicker(), 211, 0),
            new TargetPrice(StockSymbol.AXON.getTicker(), 640, 0),
            new TargetPrice(StockSymbol.CRWD.getTicker(), 443, 0),
            new TargetPrice(StockSymbol.OKTA.getTicker(), 100, 0),
            new TargetPrice(StockSymbol.GOOG.getTicker(), 167, 0),
            new TargetPrice(StockSymbol.AMZN.getTicker(), 195, 0),
            new TargetPrice(StockSymbol.GLXY.getTicker(), 16.5, 0)
    );

    private final List<TargetPrice> coinTargetPrices = List.of(
            new TargetPrice(CoinId.BITCOIN.getId(), 104000, 0),
            new TargetPrice(CoinId.ETHEREUM.getId(), 2000, 0),
            new TargetPrice(CoinId.SOLANA.getId(), 150, 0),
            new TargetPrice(CoinId.HYPERLIQUID.getId(), 30, 0)
    );

    public void addIgnoredSymbol(TickerSymbol symbol) {
        ignoredSymbols.put(symbol.getName(), new Date());
    }

    public boolean isSymbolIgnored(TickerSymbol symbol) {
        Date ignoredDate = ignoredSymbols.get(symbol.getName());
        return ignoredDate != null;
    }

    public void cleanupIgnoreSymbols() {
        long maxIgnoredDuration = 3600L; // 1 hour in seconds
        Instant now = Instant.now();
        ignoredSymbols.entrySet().removeIf(entry -> {
            Instant ignoredTime = entry.getValue().toInstant();
            return now.minusSeconds(maxIgnoredDuration).isAfter(ignoredTime);
        });
    }

}
