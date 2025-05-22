package org.tradelite.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.tradelite.common.StockTicker;
import org.tradelite.common.TargetPrice;

import java.util.List;

@Component
@Getter
public class TargetPriceManager {

    private final List<TargetPrice> targetPrices = List.of(
            new TargetPrice(StockTicker.AAPL, 200.0, 245.0),
            new TargetPrice(StockTicker.AMD, 90.0, 170.0),
            new TargetPrice(StockTicker.META, 556.0, 718.0),
            new TargetPrice(StockTicker.NFLX, 975, 0),
            new TargetPrice(StockTicker.NVDA, 105, 150),
            new TargetPrice(StockTicker.TSLA, 280, 392),
            new TargetPrice(StockTicker.COIN, 226, 0),
            new TargetPrice(StockTicker.MSTR, 300, 0),
            new TargetPrice(StockTicker.RKLB, 23, 0),
            new TargetPrice(StockTicker.UBER, 75, 0),
            new TargetPrice(StockTicker.PLTR, 112, 0),
            new TargetPrice(StockTicker.SPOT, 630, 0),
            new TargetPrice(StockTicker.HOOD, 50, 0),
            new TargetPrice(StockTicker.NET, 152, 0),
            new TargetPrice(StockTicker.AVGO, 190, 0),
            new TargetPrice(StockTicker.AXON, 600, 0),
            new TargetPrice(StockTicker.CRWD, 400, 0),
            new TargetPrice(StockTicker.OKTA, 105, 0),
            new TargetPrice(StockTicker.GOOG, 165, 0)
    );

}
