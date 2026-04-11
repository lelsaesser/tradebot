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

    /** Minimum number of data points needed for the shortest EMA (9-day). */
    static final int MIN_DATA_POINTS = 9;

    /**
     * Number of calendar days to fetch from the repository. 400 calendar days yields roughly 280+
     * trading days, giving enough data for a 200-day EMA with warm-up.
     */
    static final int LOOKBACK_CALENDAR_DAYS = 400;

    private final PriceQuoteRepository priceQuoteRepository;

    /**
     * Analyzes EMA positions for a symbol, calculating every EMA for which enough data exists.
     *
     * <p>With fewer than 200 data points, only the shorter EMAs are calculated; the rest are {@code
     * Double.NaN}. For example, 53 data points yield EMA 9, 21, and 50.
     *
     * @param symbol The stock or ETF ticker symbol
     * @param displayName Human-readable name for reporting
     * @return Analysis result, or empty if insufficient data even for the shortest EMA (9-day)
     */
    public Optional<EmaAnalysis> analyze(String symbol, String displayName) {
        List<DailyPrice> dailyPrices =
                priceQuoteRepository.findDailyClosingPrices(symbol, LOOKBACK_CALENDAR_DAYS);

        if (dailyPrices.size() < MIN_DATA_POINTS) {
            log.debug(
                    "Insufficient data for EMA analysis on {}: {} points (need at least {})",
                    symbol,
                    dailyPrices.size(),
                    MIN_DATA_POINTS);
            return Optional.empty();
        }

        List<Double> prices = dailyPrices.stream().map(DailyPrice::getPrice).toList();
        double currentPrice = prices.getLast();

        double ema9 = computeEmaOrNaN(prices, 9);
        double ema21 = computeEmaOrNaN(prices, 21);
        double ema50 = computeEmaOrNaN(prices, 50);
        double ema100 = computeEmaOrNaN(prices, 100);
        double ema200 = computeEmaOrNaN(prices, 200);

        int emasAvailable = countAvailable(ema9, ema21, ema50, ema100, ema200);
        int emasBelow = countEmasBelow(currentPrice, ema9, ema21, ema50, ema100, ema200);
        EmaSignalType signalType = EmaSignalType.fromEmasBelow(emasBelow, emasAvailable);

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
                        emasAvailable,
                        emasBelow,
                        signalType));
    }

    /**
     * Returns the calculated EMA if enough data is available, otherwise {@code Double.NaN}.
     *
     * @param prices The price series
     * @param period The EMA period
     * @return The EMA value, or NaN if insufficient data
     */
    static double computeEmaOrNaN(List<Double> prices, int period) {
        if (prices.size() < period) {
            return Double.NaN;
        }
        return StatisticsUtil.calculateEma(prices, period);
    }

    /**
     * Counts how many of the given EMAs are available (non-NaN).
     *
     * @param emas The EMA values to check
     * @return Number of available EMAs
     */
    static int countAvailable(double... emas) {
        int count = 0;
        for (double ema : emas) {
            if (!Double.isNaN(ema)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Counts how many of the available EMAs the current price is below. NaN EMAs are skipped.
     *
     * @param currentPrice The current price
     * @param emas The EMA values to compare against
     * @return Number of available EMAs the price is below
     */
    static int countEmasBelow(double currentPrice, double... emas) {
        int count = 0;
        for (double ema : emas) {
            if (!Double.isNaN(ema) && currentPrice < ema) {
                count++;
            }
        }
        return count;
    }
}
