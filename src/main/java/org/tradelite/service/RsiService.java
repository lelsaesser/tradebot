package org.tradelite.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.common.TickerSymbol;
import org.tradelite.service.model.DailyPrice;

@Slf4j
@Service
@RequiredArgsConstructor
public class RsiService {

    static final int RSI_PERIOD = 14;
    static final int RSI_LOOKBACK_DAYS = 30;

    private final DailyPriceProvider dailyPriceProvider;

    /**
     * Analyzes a single symbol for RSI overbought/oversold conditions.
     *
     * @param symbol The ticker symbol
     * @param displayName Human-readable name for reporting
     * @return Signal if overbought or oversold, empty otherwise
     */
    public Optional<RsiSignal> analyze(String symbol, String displayName) {
        List<Double> prices = fetchPrices(symbol);

        if (prices.size() < RSI_PERIOD + 1) {
            return Optional.empty();
        }

        double rsi = calculateRsi(prices);

        if (rsi >= 70) {
            return Optional.of(new RsiSignal(displayName, rsi, RsiSignal.Zone.OVERBOUGHT));
        } else if (rsi <= 30) {
            return Optional.of(new RsiSignal(displayName, rsi, RsiSignal.Zone.OVERSOLD));
        }

        return Optional.empty();
    }

    public Optional<Double> getCurrentRsi(TickerSymbol symbol) {
        List<Double> historicalPrices = fetchPrices(symbol.getName());

        if (historicalPrices.size() < RSI_PERIOD + 1) {
            return Optional.empty();
        }

        return Optional.of(calculateRsi(historicalPrices));
    }

    private List<Double> fetchPrices(String symbolKey) {
        List<DailyPrice> dailyPrices =
                dailyPriceProvider.findDailyClosingPrices(symbolKey, RSI_LOOKBACK_DAYS);
        return new ArrayList<>(dailyPrices.stream().map(DailyPrice::getPrice).toList());
    }

    /** Represents an RSI signal for a single symbol. */
    public record RsiSignal(String displayName, double rsi, Zone zone) {

        public enum Zone {
            OVERBOUGHT,
            OVERSOLD
        }
    }

    double calculateRsi(List<Double> prices) {
        if (prices.size() < RSI_PERIOD) {
            return 50; // Not enough data
        }

        double avgGain = 0;
        double avgLoss = 0;

        // First RSI value
        double firstChange = prices.get(1) - prices.get(0);
        if (firstChange > 0) {
            avgGain = firstChange;
        } else {
            avgLoss = -firstChange;
        }

        for (int i = 2; i < RSI_PERIOD; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss -= change;
            }
        }

        avgGain /= RSI_PERIOD;
        avgLoss /= RSI_PERIOD;

        // Subsequent RSI values
        for (int i = RSI_PERIOD; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? -change : 0;

            avgGain = (avgGain * (RSI_PERIOD - 1) + gain) / RSI_PERIOD;
            avgLoss = (avgLoss * (RSI_PERIOD - 1) + loss) / RSI_PERIOD;
        }

        if (avgLoss == 0) {
            return 100;
        }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
}
