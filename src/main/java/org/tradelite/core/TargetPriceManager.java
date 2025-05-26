package org.tradelite.core;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.tradelite.common.TickerSymbol;
import org.tradelite.common.TargetPrice;

import java.util.List;

@Component
@Getter
public class TargetPriceManager {

    private final List<TargetPrice> targetPrices = List.of(
            new TargetPrice(TickerSymbol.AAPL, 190.0, 245.0),
            new TargetPrice(TickerSymbol.AMD, 90.0, 170.0),
            new TargetPrice(TickerSymbol.META, 556.0, 718.0),
            new TargetPrice(TickerSymbol.NFLX, 1130, 0),
            new TargetPrice(TickerSymbol.NVDA, 105, 150),
            new TargetPrice(TickerSymbol.TSLA, 280, 392),
            new TargetPrice(TickerSymbol.COIN, 226, 0),
            new TargetPrice(TickerSymbol.MSTR, 300, 0),
            new TargetPrice(TickerSymbol.RKLB, 23, 0),
            new TargetPrice(TickerSymbol.UBER, 75, 0),
            new TargetPrice(TickerSymbol.PLTR, 112, 0),
            new TargetPrice(TickerSymbol.SPOT, 630, 0),
            new TargetPrice(TickerSymbol.HOOD, 50, 0),
            new TargetPrice(TickerSymbol.NET, 152, 0),
            new TargetPrice(TickerSymbol.AVGO, 190, 0),
            new TargetPrice(TickerSymbol.AXON, 600, 0),
            new TargetPrice(TickerSymbol.CRWD, 400, 0),
            new TargetPrice(TickerSymbol.OKTA, 105, 0),
            new TargetPrice(TickerSymbol.GOOG, 165, 0),
            new TargetPrice(TickerSymbol.AMZN, 192, 0)
    );

}
