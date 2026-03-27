package org.tradelite.quant;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

/**
 * Calculates Bollinger Bands and detects actionable signals for a given symbol.
 *
 * <p>Uses standard 20-day SMA with 2 standard deviations. Detects:
 *
 * <ul>
 *   <li>Upper/lower band touches (%B >= 1.0 or <= 0.0)
 *   <li>Squeeze: absolute bandwidth below 4% threshold (needs 20+ data points)
 *   <li>Historical squeeze: bandwidth at historically low percentile (needs 40+ data points)
 * </ul>
 *
 * <p>Requires at least 20 daily closing prices for basic analysis. Enhanced bandwidth percentile
 * history requires 40+ data points.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BollingerBandService {

    /** Standard Bollinger Band period (20-day SMA). */
    static final int PERIOD = 20;

    /** Standard deviation multiplier for upper/lower bands. */
    static final double STD_DEV_MULTIPLIER = 2.0;

    /**
     * Number of calendar days to fetch from the repository. Accounts for weekends/holidays — 90
     * calendar days yields roughly 60+ trading days.
     */
    static final int LOOKBACK_CALENDAR_DAYS = 90;

    private final PriceQuoteRepository priceQuoteRepository;

    /**
     * Analyzes Bollinger Bands for a symbol and returns the result with detected signals.
     *
     * @param symbol The stock or ETF ticker symbol
     * @param displayName Human-readable name for reporting
     * @return Analysis result, or empty if insufficient data
     */
    public Optional<BollingerBandAnalysis> analyze(String symbol, String displayName) {
        List<DailyPrice> dailyPrices =
                priceQuoteRepository.findDailyClosingPrices(symbol, LOOKBACK_CALENDAR_DAYS);

        if (dailyPrices.size() < BollingerBandAnalysis.MIN_DATA_POINTS) {
            log.debug(
                    "Insufficient data for Bollinger Bands on {}: {} points (need {})",
                    symbol,
                    dailyPrices.size(),
                    BollingerBandAnalysis.MIN_DATA_POINTS);
            return Optional.empty();
        }

        List<Double> prices = dailyPrices.stream().map(DailyPrice::getPrice).toList();

        return Optional.of(calculateBollingerBands(symbol, displayName, prices));
    }

    /**
     * Core Bollinger Band calculation. Package-private for testing.
     *
     * @param symbol Symbol identifier
     * @param displayName Display name
     * @param prices List of daily closing prices, sorted ascending by date
     * @return Bollinger Band analysis result
     */
    BollingerBandAnalysis calculateBollingerBands(
            String symbol, String displayName, List<Double> prices) {

        int n = prices.size();
        double currentPrice = prices.get(n - 1);

        // Calculate SMA over the last PERIOD prices
        double sma = StatisticsUtil.mean(prices, n - PERIOD, n);

        // Calculate standard deviation over the last PERIOD prices
        double stdDev = StatisticsUtil.populationStdDev(prices, n - PERIOD, n, sma);

        // Bollinger Bands
        double upperBand = sma + STD_DEV_MULTIPLIER * stdDev;
        double lowerBand = sma - STD_DEV_MULTIPLIER * stdDev;

        // %B = (Price - Lower) / (Upper - Lower)
        double bandWidth = upperBand - lowerBand;
        double percentB = bandWidth > 0 ? (currentPrice - lowerBand) / bandWidth : 0.5;

        // Bandwidth = (Upper - Lower) / SMA
        double bandwidth = sma > 0 ? bandWidth / sma : 0;

        // Calculate bandwidth history for percentile ranking (only if enough data)
        boolean hasBandwidthHistory = n >= BollingerBandAnalysis.BANDWIDTH_HISTORY_MIN_DATA_POINTS;
        double bandwidthPercentile = 50.0; // default when insufficient history
        if (hasBandwidthHistory) {
            List<Double> bandwidthHistory = calculateBandwidthHistory(prices);
            bandwidthPercentile = StatisticsUtil.percentile(bandwidthHistory, bandwidth);
        }

        // Detect signals
        List<BollingerSignalType> signals =
                detectSignals(percentB, bandwidth, bandwidthPercentile, hasBandwidthHistory);

        return new BollingerBandAnalysis(
                symbol,
                displayName,
                currentPrice,
                sma,
                upperBand,
                lowerBand,
                percentB,
                bandwidth,
                bandwidthPercentile,
                signals,
                n);
    }

    /**
     * Calculates a rolling bandwidth history for percentile comparison.
     *
     * <p>Computes Bollinger bandwidth for each possible 20-day window ending at positions [PERIOD,
     * n-1], giving us a history of bandwidth values to compare against.
     *
     * @param prices Full price list sorted ascending by date
     * @return List of historical bandwidth values
     */
    List<Double> calculateBandwidthHistory(List<Double> prices) {
        List<Double> history = new ArrayList<>();
        int n = prices.size();

        for (int endIdx = PERIOD; endIdx <= n; endIdx++) {
            int startIdx = endIdx - PERIOD;
            double sma = StatisticsUtil.mean(prices, startIdx, endIdx);
            double stdDev = StatisticsUtil.populationStdDev(prices, startIdx, endIdx, sma);
            double upper = sma + STD_DEV_MULTIPLIER * stdDev;
            double lower = sma - STD_DEV_MULTIPLIER * stdDev;
            double bw = sma > 0 ? (upper - lower) / sma : 0;
            history.add(bw);
        }

        return history;
    }

    /**
     * Detects actionable Bollinger Band signals based on %B, absolute bandwidth, and bandwidth
     * percentile.
     *
     * @param percentB Current %B value
     * @param bandwidth Current absolute bandwidth (upper - lower) / SMA
     * @param bandwidthPercentile Current bandwidth percentile vs. history
     * @param hasBandwidthHistory Whether sufficient data exists for percentile comparison
     * @return List of detected signals (may be empty)
     */
    static List<BollingerSignalType> detectSignals(
            double percentB,
            double bandwidth,
            double bandwidthPercentile,
            boolean hasBandwidthHistory) {
        List<BollingerSignalType> signals = new ArrayList<>();

        if (percentB >= 1.0) {
            signals.add(BollingerSignalType.UPPER_BAND_TOUCH);
        } else if (percentB <= 0.0) {
            signals.add(BollingerSignalType.LOWER_BAND_TOUCH);
        }

        // Absolute bandwidth squeeze — works with just 20 data points
        if (bandwidth <= BollingerBandAnalysis.SQUEEZE_BANDWIDTH_THRESHOLD) {
            signals.add(BollingerSignalType.SQUEEZE);
        }

        // Historical squeeze — only when we have enough bandwidth history
        if (hasBandwidthHistory
                && bandwidthPercentile <= BollingerBandAnalysis.SQUEEZE_PERCENTILE_THRESHOLD) {
            signals.add(BollingerSignalType.HISTORICAL_SQUEEZE);
        }

        return Collections.unmodifiableList(signals);
    }
}
