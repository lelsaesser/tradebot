package org.tradelite.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;

import java.util.List;

@Component
@Getter
public class TargetPriceManager {

    private final List<TargetPrice> targetPrices = List.of(
            new TargetPrice(StockSymbol.AAPL.getTicker(), 190.0, 245.0),
            new TargetPrice(StockSymbol.AMD.getTicker(), 90.0, 170.0),
            new TargetPrice(StockSymbol.META.getTicker(), 556.0, 718.0),
            new TargetPrice(StockSymbol.NFLX.getTicker(), 1130, 0),
            new TargetPrice(StockSymbol.NVDA.getTicker(), 105, 150),
            new TargetPrice(StockSymbol.TSLA.getTicker(), 280, 392),
            new TargetPrice(StockSymbol.COIN.getTicker(), 226, 0),
            new TargetPrice(StockSymbol.MSTR.getTicker(), 300, 0),
            new TargetPrice(StockSymbol.RKLB.getTicker(), 23, 0),
            new TargetPrice(StockSymbol.UBER.getTicker(), 75, 0),
            new TargetPrice(StockSymbol.PLTR.getTicker(), 112, 0),
            new TargetPrice(StockSymbol.SPOT.getTicker(), 630, 0),
            new TargetPrice(StockSymbol.HOOD.getTicker(), 50, 0),
            new TargetPrice(StockSymbol.NET.getTicker(), 152, 0),
            new TargetPrice(StockSymbol.AVGO.getTicker(), 190, 0),
            new TargetPrice(StockSymbol.AXON.getTicker(), 600, 0),
            new TargetPrice(StockSymbol.CRWD.getTicker(), 400, 0),
            new TargetPrice(StockSymbol.OKTA.getTicker(), 105, 0),
            new TargetPrice(StockSymbol.GOOG.getTicker(), 165, 0),
            new TargetPrice(StockSymbol.AMZN.getTicker(), 192, 0)
    );

    private final List<TargetPrice> coinTargetPrices = List.of(
            new TargetPrice(CoinId.BITCOIN.getId(), 100000, 0)
    );

}
