package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.core.RelativeStrengthSignal;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.RelativeStrengthData;
import org.tradelite.service.model.RsiDailyClosePrice;

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

    /** Minimum RS history required for EMA calculation */
    private static final int MIN_HISTORY_SIZE = EMA_PERIOD;

    /** File to persist RS data */
    private static final String RS_DATA_FILE = "config/rs-data.json";

    private final ObjectMapper objectMapper;
    private final RsiService rsiService;

    @Getter private Map<String, RelativeStrengthData> rsHistory = new HashMap<>();

    @Autowired
    public RelativeStrengthService(ObjectMapper objectMapper, RsiService rsiService)
            throws IOException {
        this.objectMapper = objectMapper;
        this.rsiService = rsiService;
        loadRsHistory();
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

        // Get price history for stock and SPY
        Map<String, RsiDailyClosePrice> priceHistory = rsiService.getPriceHistory();
        RsiDailyClosePrice stockPriceData = priceHistory.get(symbol);
        RsiDailyClosePrice spyPriceData = priceHistory.get(BENCHMARK_SYMBOL);

        if (stockPriceData == null || spyPriceData == null) {
            log.debug(
                    "Insufficient price data for RS calculation. Stock={}, SPY={}",
                    stockPriceData != null,
                    spyPriceData != null);
            return Optional.empty();
        }

        // Calculate RS values for matching dates
        List<DailyPrice> stockPrices = stockPriceData.getPrices();
        List<DailyPrice> spyPrices = spyPriceData.getPrices();

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
            log.debug(
                    "Insufficient RS history for {}: {} values (need {})",
                    symbol,
                    rsValues.size(),
                    MIN_HISTORY_SIZE);
            return Optional.empty();
        }

        // Calculate current RS and EMA
        double currentRs = rsValues.getLast();
        double currentEma = calculateEma(rsValues, EMA_PERIOD);

        // Detect crossover
        Optional<RelativeStrengthSignal> signal =
                detectCrossover(symbol, displayName, rsData, currentRs, currentEma);

        // Update state for next crossover detection
        rsData.setPreviousRs(currentRs);
        rsData.setPreviousEma(currentEma);
        rsData.setInitialized(true);

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
            log.debug("First RS calculation for {}, no crossover detection yet", symbol);
            return Optional.empty();
        }

        double previousRs = rsData.getPreviousRs();
        double previousEma = rsData.getPreviousEma();

        // Crossover up: was below EMA, now above EMA
        boolean wasBelow = previousRs < previousEma;
        boolean isAbove = currentRs > currentEma;
        boolean crossoverUp = wasBelow && isAbove;

        // Crossover down: was above EMA, now below EMA
        boolean wasAbove = previousRs > previousEma;
        boolean isBelow = currentRs < currentEma;
        boolean crossoverDown = wasAbove && isBelow;

        if (crossoverUp) {
            double percentageDiff = ((currentRs - currentEma) / currentEma) * 100;
            log.info(
                    "RS crossover UP for {}: RS={}, EMA={} (+{}%)",
                    symbol, currentRs, currentEma, percentageDiff);
            return Optional.of(
                    new RelativeStrengthSignal(
                            symbol,
                            displayName,
                            RelativeStrengthSignal.SignalType.OUTPERFORMING,
                            currentRs,
                            currentEma,
                            percentageDiff));
        }

        if (crossoverDown) {
            double percentageDiff = ((currentRs - currentEma) / currentEma) * 100;
            log.info(
                    "RS crossover DOWN for {}: RS={}, EMA={} ({}%)",
                    symbol, currentRs, currentEma, percentageDiff);
            return Optional.of(
                    new RelativeStrengthSignal(
                            symbol,
                            displayName,
                            RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                            currentRs,
                            currentEma,
                            percentageDiff));
        }

        return Optional.empty();
    }

    /**
     * Calculates Exponential Moving Average (EMA).
     *
     * <p>Formula: EMA = (Current - Previous EMA) * multiplier + Previous EMA Where multiplier = 2 /
     * (period + 1)
     *
     * <p>For the first EMA calculation, uses SMA (Simple Moving Average) as the seed.
     *
     * @param values List of values in chronological order
     * @param period The EMA period (e.g., 50)
     * @return The calculated EMA value
     */
    protected double calculateEma(List<Double> values, int period) {
        if (values.size() < period) {
            return 0;
        }

        double multiplier = 2.0 / (period + 1);

        // Calculate initial SMA for the first 'period' values
        double sma = 0;
        for (int i = 0; i < period; i++) {
            sma += values.get(i);
        }
        sma /= period;

        // Apply EMA formula starting from the period-th value
        double ema = sma;
        for (int i = period; i < values.size(); i++) {
            ema = (values.get(i) - ema) * multiplier + ema;
        }

        return ema;
    }

    /**
     * Gets the current RS and EMA values for a symbol.
     *
     * @param symbol The stock ticker symbol
     * @return Optional containing [RS, EMA] array, or empty if insufficient data
     */
    public Optional<double[]> getCurrentRsAndEma(String symbol) {
        RelativeStrengthData rsData = rsHistory.get(symbol);
        if (rsData == null) {
            return Optional.empty();
        }

        List<Double> rsValues = rsData.getRsValues();
        if (rsValues.size() < MIN_HISTORY_SIZE) {
            return Optional.empty();
        }

        double currentRs = rsValues.getLast();
        double currentEma = calculateEma(rsValues, EMA_PERIOD);

        return Optional.of(new double[] {currentRs, currentEma});
    }

    /** Persists RS history to file. */
    public void saveRsHistory() throws IOException {
        try {
            objectMapper.writeValue(new File(RS_DATA_FILE), rsHistory);
        } catch (IOException e) {
            log.error("Error saving RS data", e);
            throw e;
        }
    }

    /** Loads RS history from file. */
    protected void loadRsHistory() throws IOException {
        try {
            File file = new File(RS_DATA_FILE);
            if (file.exists()) {
                rsHistory =
                        objectMapper.readValue(
                                file,
                                objectMapper
                                        .getTypeFactory()
                                        .constructMapType(
                                                HashMap.class,
                                                String.class,
                                                RelativeStrengthData.class));
            }
        } catch (IOException e) {
            log.error("Error loading RS data", e);
            throw e;
        }
    }
}
