package org.tradelite.service;

import java.time.LocalDate;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.core.RelativeStrengthSignal;
import org.tradelite.quant.StatisticsUtil;
import org.tradelite.repository.RsCrossoverStateRepository;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.RelativeStrengthData;

/**
 * Service for calculating Relative Strength (RS) vs SPY benchmark and detecting crossovers.
 *
 * <p>The algorithm replicates the TradingView "Relative Strength vs Benchmark" indicator: - RS =
 * stockClose / SPY_close (price ratio) - RS EMA = 50-period EMA of the RS value - Alert when RS
 * crosses above EMA (outperforming) or below EMA (underperforming)
 */
@Slf4j
@Service
public class RelativeStrengthService {

    /** SPY ticker symbol used as benchmark */
    public static final String BENCHMARK_SYMBOL = "SPY";

    /** EMA period (default 50, matching TradingView indicator) */
    private static final int EMA_PERIOD = 50;

    /** Minimum RS history required for EMA calculation (allows calculation with limited data) */
    private static final int MIN_HISTORY_SIZE = 10;

    /** Number of calendar days to fetch for RS calculation (with buffer for weekends/holidays) */
    private static final int RS_LOOKBACK_DAYS = 80;

    /**
     * Dead zone around the EMA (percentage-based) to filter noise. RS must deviate more than
     * ±RS_EMA_DEAD_ZONE percent from EMA to count as meaningfully above or below. This prevents
     * false crossover alerts when RS oscillates tightly around the EMA in range-bound markets.
     */
    static final double RS_EMA_DEAD_ZONE = 0.2;

    /** Result of RS and EMA calculation with data completeness info */
    public record RsResult(double rs, double ema, int dataPoints, boolean isComplete) {
        /** Returns true if we have at least the full EMA period of data */
        public boolean isComplete() {
            return dataPoints >= EMA_PERIOD;
        }
    }

    private final RsCrossoverStateRepository rsCrossoverStateRepository;
    private final DailyPriceProvider dailyPriceProvider;

    @Getter private final Map<String, RelativeStrengthData> rsHistory;

    @Autowired
    public RelativeStrengthService(
            RsCrossoverStateRepository rsCrossoverStateRepository,
            DailyPriceProvider dailyPriceProvider) {
        this.rsCrossoverStateRepository = rsCrossoverStateRepository;
        this.dailyPriceProvider = dailyPriceProvider;
        this.rsHistory = new HashMap<>(rsCrossoverStateRepository.findAll());
    }

    /**
     * Calculates relative strength for a stock against SPY and detects crossovers.
     *
     * @param symbol The stock ticker symbol
     * @param displayName The display name of the stock
     * @return Optional containing a signal if a crossover was detected
     */
    public Optional<RelativeStrengthSignal> calculateRelativeStrength(
            String symbol, String displayName) {

        // Skip benchmark itself
        if (BENCHMARK_SYMBOL.equals(symbol)) {
            return Optional.empty();
        }

        // Get price history for stock and SPY from DailyPriceProvider
        List<DailyPrice> stockPrices =
                dailyPriceProvider.findDailyClosingPrices(symbol, RS_LOOKBACK_DAYS);
        List<DailyPrice> spyPrices =
                dailyPriceProvider.findDailyClosingPrices(BENCHMARK_SYMBOL, RS_LOOKBACK_DAYS);

        if (stockPrices.isEmpty() || spyPrices.isEmpty()) {
            log.info(
                    "Insufficient price data for RS calculation. Stock={}, SPY={}",
                    !stockPrices.isEmpty(),
                    !spyPrices.isEmpty());
            return Optional.empty();
        }

        // Build a map of SPY prices by date for efficient lookup
        Map<LocalDate, Double> spyPriceMap = new HashMap<>();
        for (DailyPrice price : spyPrices) {
            spyPriceMap.put(price.getDate(), price.getPrice());
        }

        // Get or create RS data for this symbol
        RelativeStrengthData rsData = rsHistory.getOrDefault(symbol, new RelativeStrengthData());

        // Calculate RS for each date where we have both stock and SPY prices
        for (DailyPrice stockPrice : stockPrices) {
            Double spyPrice = spyPriceMap.get(stockPrice.getDate());
            if (spyPrice != null && spyPrice > 0) {
                double rs = stockPrice.getPrice() / spyPrice;
                rsData.addRsValue(stockPrice.getDate(), rs);
            }
        }

        rsHistory.put(symbol, rsData);

        // Check if we have enough data for EMA calculation
        List<Double> rsValues = rsData.getRsValues();
        if (rsValues.size() < MIN_HISTORY_SIZE) {
            log.info(
                    "Insufficient RS history for {}: {} values (need {})",
                    symbol,
                    rsValues.size(),
                    MIN_HISTORY_SIZE);
            return Optional.empty();
        }

        // Calculate current RS and EMA
        double currentRs = rsValues.getLast();
        double currentEma = StatisticsUtil.calculateEma(rsValues, EMA_PERIOD);

        // Detect crossover
        Optional<RelativeStrengthSignal> signal =
                detectCrossover(symbol, displayName, rsData, currentRs, currentEma);

        // Update state for next crossover detection
        rsData.setPreviousRs(currentRs);
        rsData.setPreviousEma(currentEma);
        rsData.setInitialized(true);

        // Persist crossover state
        rsCrossoverStateRepository.save(symbol, rsData);

        return signal;
    }

    /**
     * Detects if RS has crossed above or below its EMA.
     *
     * @param symbol The stock ticker symbol
     * @param displayName The display name
     * @param rsData The RS data containing previous values
     * @param currentRs Current RS value
     * @param currentEma Current EMA value
     * @return Optional signal if crossover detected
     */
    private Optional<RelativeStrengthSignal> detectCrossover(
            String symbol,
            String displayName,
            RelativeStrengthData rsData,
            double currentRs,
            double currentEma) {

        // Skip if not initialized (first calculation)
        if (!rsData.isInitialized()) {
            log.info("First RS calculation for {}, no crossover detection yet", symbol);
            return Optional.empty();
        }

        double previousRs = rsData.getPreviousRs();
        double previousEma = rsData.getPreviousEma();

        double previousPctDiff = ((previousRs - previousEma) / previousEma) * 100;
        double currentPctDiff = ((currentRs - currentEma) / currentEma) * 100;

        // Crossover up: was meaningfully below EMA, now meaningfully above EMA
        boolean wasMeaningfullyBelow = previousPctDiff < -RS_EMA_DEAD_ZONE;
        boolean isMeaningfullyAbove = currentPctDiff > RS_EMA_DEAD_ZONE;
        boolean crossoverUp = wasMeaningfullyBelow && isMeaningfullyAbove;

        // Crossover down: was meaningfully above EMA, now meaningfully below EMA
        boolean wasMeaningfullyAbove = previousPctDiff > RS_EMA_DEAD_ZONE;
        boolean isMeaningfullyBelow = currentPctDiff < -RS_EMA_DEAD_ZONE;
        boolean crossoverDown = wasMeaningfullyAbove && isMeaningfullyBelow;

        if (crossoverUp) {
            log.info(
                    "RS crossover UP for {}: RS={}, EMA={} (+{}%)",
                    symbol, currentRs, currentEma, currentPctDiff);
            return Optional.of(
                    new RelativeStrengthSignal(
                            symbol,
                            displayName,
                            RelativeStrengthSignal.SignalType.OUTPERFORMING,
                            currentRs,
                            currentEma,
                            currentPctDiff));
        }

        if (crossoverDown) {
            log.info(
                    "RS crossover DOWN for {}: RS={}, EMA={} ({}%)",
                    symbol, currentRs, currentEma, currentPctDiff);
            return Optional.of(
                    new RelativeStrengthSignal(
                            symbol,
                            displayName,
                            RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                            currentRs,
                            currentEma,
                            currentPctDiff));
        }

        return Optional.empty();
    }

    /**
     * Gets the current RS and EMA values for a symbol.
     *
     * <p>This method fetches fresh price data from SQLite to ensure up-to-date values even if the
     * application has been running for an extended period.
     *
     * @param symbol The stock ticker symbol
     * @return Optional containing [RS, EMA] array, or empty if insufficient data
     */
    public Optional<double[]> getCurrentRsAndEma(String symbol) {
        return getCurrentRsResult(symbol).map(result -> new double[] {result.rs(), result.ema()});
    }

    /**
     * Gets the current RS calculation result with data completeness info.
     *
     * <p>This method fetches fresh price data from SQLite and includes metadata about whether the
     * calculation was based on complete data (at least 50 data points for the EMA period).
     *
     * @param symbol The stock ticker symbol
     * @return Optional containing RsResult with RS, EMA, and completeness info
     */
    public Optional<RsResult> getCurrentRsResult(String symbol) {
        // Skip benchmark itself
        if (BENCHMARK_SYMBOL.equals(symbol)) {
            return Optional.empty();
        }

        // Fetch fresh daily prices from SQLite (only last 80 days)
        List<DailyPrice> stockPrices =
                dailyPriceProvider.findDailyClosingPrices(symbol, RS_LOOKBACK_DAYS);
        List<DailyPrice> spyPrices =
                dailyPriceProvider.findDailyClosingPrices(BENCHMARK_SYMBOL, RS_LOOKBACK_DAYS);

        if (stockPrices.isEmpty() || spyPrices.isEmpty()) {
            log.debug(
                    "Insufficient price data for RS calculation. Stock={}, SPY={}",
                    !stockPrices.isEmpty(),
                    !spyPrices.isEmpty());
            return Optional.empty();
        }

        // Build a map of SPY prices by date for efficient lookup
        Map<LocalDate, Double> spyPriceMap = new HashMap<>();
        for (DailyPrice price : spyPrices) {
            spyPriceMap.put(price.getDate(), price.getPrice());
        }

        // Calculate RS values for each date where we have both stock and SPY prices
        List<Double> rsValues = new ArrayList<>();
        for (DailyPrice stockPrice : stockPrices) {
            Double spyPrice = spyPriceMap.get(stockPrice.getDate());
            if (spyPrice != null && spyPrice > 0) {
                double rs = stockPrice.getPrice() / spyPrice;
                rsValues.add(rs);
            }
        }

        if (rsValues.size() < MIN_HISTORY_SIZE) {
            log.debug(
                    "Insufficient RS history for {}: {} values (need {})",
                    symbol,
                    rsValues.size(),
                    MIN_HISTORY_SIZE);
            return Optional.empty();
        }

        double currentRs = rsValues.getLast();
        // Use available data for EMA, even if less than full period
        int emaPeriod = Math.min(rsValues.size(), EMA_PERIOD);
        double currentEma = StatisticsUtil.calculateEma(rsValues, emaPeriod);
        boolean isComplete = rsValues.size() >= EMA_PERIOD;

        return Optional.of(new RsResult(currentRs, currentEma, rsValues.size(), isComplete));
    }
}
