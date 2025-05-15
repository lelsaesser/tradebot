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
            new TargetPrice(StockTicker.TSLA, 250, 392),
            new TargetPrice(StockTicker.COIN, 176, 280),
            new TargetPrice(StockTicker.MSTR, 300, 0),
            new TargetPrice(StockTicker.RKLB, 18, 0),
            new TargetPrice(StockTicker.UBER, 75, 0),
            new TargetPrice(StockTicker.PLTR, 112, 0),
            new TargetPrice(StockTicker.SPOT, 610, 0),
            new TargetPrice(StockTicker.HOOD, 45, 62)
    );

}
