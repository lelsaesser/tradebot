package org.tradelite.quant;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

/**
 * Calculates Exponential Moving Averages (EMA) for 9, 21, 50, 100, and 200 day periods.
 *
 * <p>Uses historical daily closing prices from the repository and classifies each symbol based on
 * how many EMAs its current price is below. Delegates EMA calculation to {@link
 * StatisticsUtil#calculateEma(List, int)}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmaService {

    /** Minimum number of data points needed for the longest EMA (200-day). */
    static final int MIN_DATA_POINTS = 200;

    /**
     * Number of calendar days to fetch from the repository. 400 calendar days yields roughly 280+
     * trading days, giving enough data for a 200-day EMA with warm-up.
     */
    static final int LOOKBACK_CALENDAR_DAYS = 400;

    private final PriceQuoteRepository priceQuoteRepository;

    /**
     * Analyzes EMA positions for a symbol and returns the result.
     *
     * @param symbol The stock or ETF ticker symbol
     * @param displayName Human-readable name for reporting
     * @return Analysis result, or empty if insufficient data
     */
    public Optional<EmaAnalysis> analyze(String symbol, String displayName) {
        List<DailyPrice> dailyPrices =
                priceQuoteRepository.findDailyClosingPrices(symbol, LOOKBACK_CALENDAR_DAYS);

        if (dailyPrices.size() < MIN_DATA_POINTS) {
            log.debug(
                    "Insufficient data for EMA analysis on {}: {} points (need {})",
                    symbol,
                    dailyPrices.size(),
                    MIN_DATA_POINTS);
            return Optional.empty();
        }

        List<Double> prices = dailyPrices.stream().map(DailyPrice::getPrice).toList();
        double currentPrice = prices.getLast();

        double ema9 = StatisticsUtil.calculateEma(prices, 9);
        double ema21 = StatisticsUtil.calculateEma(prices, 21);
        double ema50 = StatisticsUtil.calculateEma(prices, 50);
        double ema100 = StatisticsUtil.calculateEma(prices, 100);
        double ema200 = StatisticsUtil.calculateEma(prices, 200);

        int emasBelow = countEmasBelow(currentPrice, ema9, ema21, ema50, ema100, ema200);
        EmaSignalType signalType = EmaSignalType.fromEmasBelow(emasBelow);

        return Optional.of(
                new EmaAnalysis(
                        symbol,
                        displayName,
                        currentPrice,
                        ema9,
                        ema21,
                        ema50,
                        ema100,
                        ema200,
                        emasBelow,
                        signalType));
    }

    /**
     * Counts how many of the 5 EMAs the current price is below.
     *
     * @param currentPrice The current price
     * @param emas The EMA values to compare against
     * @return Number of EMAs the price is below (0-5)
     */
    static int countEmasBelow(double currentPrice, double... emas) {
        int count = 0;
        for (double ema : emas) {
            if (currentPrice < ema) {
                count++;
            }
        }
        return count;
    }
}
