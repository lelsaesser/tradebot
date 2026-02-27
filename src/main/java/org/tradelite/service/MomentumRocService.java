package org.tradelite.service;

import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.core.MomentumRocSignal;
import org.tradelite.core.MomentumRocSignal.SignalType;
import org.tradelite.repository.MomentumRocRepository;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.MomentumRocData;

/**
 * Service for calculating Rate of Change (ROC) momentum and detecting zero-line crossovers.
 *
 * <p>ROC measures the percentage change in price over a specified period. When ROC crosses above
 * zero, momentum is turning positive (bullish). When ROC crosses below zero, momentum is turning
 * negative (bearish).
 *
 * <p>Formula: ROC = ((Current Price - Price N days ago) / Price N days ago) × 100
 */
@Slf4j
@Service
public class MomentumRocService {

    /** Short-term ROC period (10 trading days) */
    private static final int ROC_SHORT_PERIOD = 10;

    /** Long-term ROC period (20 trading days) */
    private static final int ROC_LONG_PERIOD = 20;

    /** Number of calendar days to fetch for ROC calculation (with buffer for weekends/holidays) */
    private static final int ROC_LOOKBACK_DAYS = 35;

    /** Minimum data points required for reliable ROC calculation */
    private static final int MIN_DATA_POINTS = 21;

    private final PriceQuoteRepository priceQuoteRepository;
    private final MomentumRocRepository momentumRocRepository;

    @Autowired
    public MomentumRocService(
            PriceQuoteRepository priceQuoteRepository,
            MomentumRocRepository momentumRocRepository) {
        this.priceQuoteRepository = priceQuoteRepository;
        this.momentumRocRepository = momentumRocRepository;
    }

    /** Result of ROC calculation with data completeness info */
    public record RocResult(double roc10, double roc20, int dataPoints, boolean isComplete) {
        /** Returns true if we have enough data for reliable ROC calculation */
        public boolean isComplete() {
            return dataPoints >= MIN_DATA_POINTS;
        }
    }

    /**
     * Calculates ROC for a symbol and detects momentum crossovers.
     *
     * @param symbol The stock ticker symbol
     * @param displayName The display name of the stock
     * @return Optional containing a signal if a zero-line crossover was detected
     */
    public Optional<MomentumRocSignal> detectMomentumShift(String symbol, String displayName) {
        Optional<RocResult> rocResultOpt = calculateRoc(symbol);

        if (rocResultOpt.isEmpty()) {
            return Optional.empty();
        }

        RocResult rocResult = rocResultOpt.get();

        // Get or create momentum data for this symbol from SQLite
        MomentumRocData momentumData =
                momentumRocRepository.findBySymbol(symbol).orElseGet(MomentumRocData::new);

        // Detect crossover
        Optional<MomentumRocSignal> signal =
                detectCrossover(symbol, displayName, momentumData, rocResult);

        // Update state for next crossover detection
        momentumData.setPreviousRoc10(rocResult.roc10());
        momentumData.setPreviousRoc20(rocResult.roc20());
        momentumData.setInitialized(true);
        momentumRocRepository.save(symbol, momentumData);

        return signal;
    }

    /**
     * Calculates ROC values for a symbol from SQLite historical data.
     *
     * @param symbol The stock ticker symbol
     * @return Optional containing RocResult with ROC values and data info
     */
    public Optional<RocResult> calculateRoc(String symbol) {
        List<DailyPrice> prices =
                priceQuoteRepository.findDailyClosingPrices(symbol, ROC_LOOKBACK_DAYS);

        if (prices.size() < MIN_DATA_POINTS) {
            log.debug(
                    "Insufficient price data for ROC calculation of {}: {} days (need {})",
                    symbol,
                    prices.size(),
                    MIN_DATA_POINTS);
            return Optional.empty();
        }

        double roc10 = calculateRocValue(prices, ROC_SHORT_PERIOD);
        double roc20 = calculateRocValue(prices, ROC_LONG_PERIOD);

        return Optional.of(
                new RocResult(roc10, roc20, prices.size(), prices.size() >= MIN_DATA_POINTS));
    }

    /**
     * Calculates ROC for a given period.
     *
     * <p>ROC = ((Current Price - Price N days ago) / Price N days ago) × 100
     *
     * @param prices List of daily prices sorted by date ascending
     * @param period The ROC period (number of days)
     * @return The ROC percentage value
     */
    protected double calculateRocValue(List<DailyPrice> prices, int period) {
        if (prices.size() <= period) {
            return 0;
        }

        double currentPrice = prices.getLast().getPrice();
        double pastPrice = prices.get(prices.size() - 1 - period).getPrice();

        if (pastPrice == 0) {
            return 0;
        }

        return ((currentPrice - pastPrice) / pastPrice) * 100;
    }

    /**
     * Detects if ROC has crossed the zero line.
     *
     * @param symbol The stock ticker symbol
     * @param displayName The display name
     * @param momentumData The momentum data containing previous values
     * @param rocResult Current ROC values
     * @return Optional signal if crossover detected
     */
    private Optional<MomentumRocSignal> detectCrossover(
            String symbol, String displayName, MomentumRocData momentumData, RocResult rocResult) {

        // Skip if not initialized (first calculation)
        if (!momentumData.isInitialized()) {
            log.info("First ROC calculation for {}, no crossover detection yet", symbol);
            return Optional.empty();
        }

        double previousRoc10 = momentumData.getPreviousRoc10();
        double currentRoc10 = rocResult.roc10();

        // Crossover positive: was below zero, now above zero
        boolean wasNegative = previousRoc10 < 0;
        boolean isPositive = currentRoc10 > 0;
        boolean crossoverPositive = wasNegative && isPositive;

        // Crossover negative: was above zero, now below zero
        boolean wasPositive = previousRoc10 > 0;
        boolean isNegative = currentRoc10 < 0;
        boolean crossoverNegative = wasPositive && isNegative;

        if (crossoverPositive) {
            log.info(
                    "ROC crossover POSITIVE for {}: ROC10={}%, ROC20={}%",
                    symbol, currentRoc10, rocResult.roc20());
            return Optional.of(
                    new MomentumRocSignal(
                            symbol,
                            displayName,
                            SignalType.MOMENTUM_TURNING_POSITIVE,
                            currentRoc10,
                            rocResult.roc20(),
                            previousRoc10,
                            momentumData.getPreviousRoc20()));
        }

        if (crossoverNegative) {
            log.info(
                    "ROC crossover NEGATIVE for {}: ROC10={}%, ROC20={}%",
                    symbol, currentRoc10, rocResult.roc20());
            return Optional.of(
                    new MomentumRocSignal(
                            symbol,
                            displayName,
                            SignalType.MOMENTUM_TURNING_NEGATIVE,
                            currentRoc10,
                            rocResult.roc20(),
                            previousRoc10,
                            momentumData.getPreviousRoc20()));
        }

        return Optional.empty();
    }
}
