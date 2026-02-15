package org.tradelite.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.finviz.dto.IndustryPerformance;

/**
 * Analyzes sector performance data to detect rotation signals using Z-Score statistical analysis.
 *
 * <p>The algorithm uses standard deviation-based thresholds to identify sectors that are
 * significantly outperforming or underperforming relative to historical norms. This provides
 * conservative, high-confidence signals that adapt to market conditions.
 */
@Slf4j
@Component
public class SectorRotationAnalyzer {

    // Z-score threshold for HIGH confidence signals (2 standard deviations)
    private static final double HIGH_CONFIDENCE_THRESHOLD = 2.0;

    // Z-score threshold for MEDIUM confidence signals (1.5 standard deviations)
    private static final double MEDIUM_CONFIDENCE_THRESHOLD = 1.5;

    // Minimum number of historical snapshots required for reliable analysis
    private static final int MIN_HISTORY_SIZE = 5;

    /**
     * Analyzes sector performance history to detect rotation signals.
     *
     * @param history List of performance snapshots ordered chronologically
     * @return List of detected rotation signals (empty if insufficient data)
     */
    public List<RotationSignal> analyzeRotations(List<SectorPerformanceSnapshot> history) {
        if (history == null || history.size() < MIN_HISTORY_SIZE) {
            log.info(
                    "Insufficient history for rotation analysis. Need at least {} snapshots, have {}",
                    MIN_HISTORY_SIZE,
                    history == null ? 0 : history.size());
            return List.of();
        }

        // Get the latest snapshot for current performance values
        SectorPerformanceSnapshot latestSnapshot = history.getLast();

        // Calculate historical statistics for each sector
        Map<String, SectorStats> historicalStats = calculateHistoricalStats(history);

        List<RotationSignal> signals = new ArrayList<>();

        for (IndustryPerformance current : latestSnapshot.performances()) {
            SectorStats stats = historicalStats.get(current.name());
            if (stats == null || !stats.hasEnoughData()) {
                continue;
            }

            // Calculate z-scores for weekly and monthly performance
            double zScoreWeekly =
                    calculateZScore(current.perfWeek(), stats.weeklyMean, stats.weeklyStdDev);
            double zScoreMonthly =
                    calculateZScore(current.perfMonth(), stats.monthlyMean, stats.monthlyStdDev);

            // Determine if this sector has a significant rotation signal
            RotationSignal signal = evaluateSignal(current, zScoreWeekly, zScoreMonthly);
            if (signal != null) {
                signals.add(signal);
                log.debug(
                        "Detected {} signal for {}: zWeekly={:.2f}, zMonthly={:.2f}",
                        signal.signalType(),
                        signal.sectorName(),
                        zScoreWeekly,
                        zScoreMonthly);
            }
        }

        log.info(
                "Analyzed {} sectors, found {} rotation signals",
                latestSnapshot.performances().size(),
                signals.size());
        return signals;
    }

    /**
     * Calculates historical mean and standard deviation for each sector's weekly and monthly
     * performance.
     */
    private Map<String, SectorStats> calculateHistoricalStats(
            List<SectorPerformanceSnapshot> history) {
        Map<String, List<Double>> weeklyValues = new HashMap<>();
        Map<String, List<Double>> monthlyValues = new HashMap<>();

        // Collect all historical values for each sector
        for (SectorPerformanceSnapshot snapshot : history) {
            for (IndustryPerformance perf : snapshot.performances()) {
                weeklyValues.computeIfAbsent(perf.name(), k -> new ArrayList<>());
                monthlyValues.computeIfAbsent(perf.name(), k -> new ArrayList<>());

                if (perf.perfWeek() != null) {
                    weeklyValues.get(perf.name()).add(perf.perfWeek().doubleValue());
                }
                if (perf.perfMonth() != null) {
                    monthlyValues.get(perf.name()).add(perf.perfMonth().doubleValue());
                }
            }
        }

        // Calculate statistics for each sector
        Map<String, SectorStats> stats = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : weeklyValues.entrySet()) {
            String sectorName = entry.getKey();
            List<Double> weekly = entry.getValue();
            List<Double> monthly = monthlyValues.getOrDefault(sectorName, List.of());

            double weeklyMean = calculateMean(weekly);
            double weeklyStdDev = calculateStdDev(weekly, weeklyMean);
            double monthlyMean = calculateMean(monthly);
            double monthlyStdDev = calculateStdDev(monthly, monthlyMean);

            int weeklySize;
            int monthlySize;
            if (weekly == null) {
                weeklySize = 0;
            } else {
                weeklySize = weekly.size();
            }
            if (monthly == null) {
                monthlySize = 0;
            } else {
                monthlySize = monthly.size();
            }

            stats.put(
                    sectorName,
                    new SectorStats(
                            weeklyMean,
                            weeklyStdDev,
                            monthlyMean,
                            monthlyStdDev,
                            weeklySize,
                            monthlySize));
        }

        return stats;
    }

    /** Evaluates whether the current performance represents a significant rotation signal. */
    private RotationSignal evaluateSignal(
            IndustryPerformance current, double zScoreWeekly, double zScoreMonthly) {
        // Check if both z-scores have the same direction (both positive or both negative)
        boolean sameDirection =
                (zScoreWeekly >= 0 && zScoreMonthly >= 0)
                        || (zScoreWeekly < 0 && zScoreMonthly < 0);

        if (!sameDirection) {
            // Diverging signals - not a clear rotation, skip
            return null;
        }

        double absZWeekly = Math.abs(zScoreWeekly);
        double absZMonthly = Math.abs(zScoreMonthly);

        // Determine confidence level
        RotationSignal.Confidence confidence;
        if (absZWeekly >= HIGH_CONFIDENCE_THRESHOLD && absZMonthly >= HIGH_CONFIDENCE_THRESHOLD) {
            confidence = RotationSignal.Confidence.HIGH;
        } else if (absZWeekly >= HIGH_CONFIDENCE_THRESHOLD
                || absZMonthly >= HIGH_CONFIDENCE_THRESHOLD) {
            confidence = RotationSignal.Confidence.MEDIUM;
        } else if (absZWeekly >= MEDIUM_CONFIDENCE_THRESHOLD
                && absZMonthly >= MEDIUM_CONFIDENCE_THRESHOLD) {
            confidence = RotationSignal.Confidence.MEDIUM;
        } else {
            // Below threshold - no significant signal
            return null;
        }

        // For conservative alerts, only return HIGH confidence signals
        if (confidence != RotationSignal.Confidence.HIGH) {
            return null;
        }

        // Determine signal type based on direction
        RotationSignal.SignalType signalType =
                zScoreWeekly > 0
                        ? RotationSignal.SignalType.ROTATING_IN
                        : RotationSignal.SignalType.ROTATING_OUT;

        return new RotationSignal(
                current.name(),
                signalType,
                current.perfWeek(),
                current.perfMonth(),
                zScoreWeekly,
                zScoreMonthly,
                confidence);
    }

    /** Calculates the z-score: (value - mean) / stdDev */
    private double calculateZScore(BigDecimal value, double mean, double stdDev) {
        if (value == null || stdDev == 0) {
            return 0;
        }
        return (value.doubleValue() - mean) / stdDev;
    }

    /** Calculates the arithmetic mean of a list of values. */
    private double calculateMean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /** Calculates the standard deviation of a list of values. */
    private double calculateStdDev(List<Double> values, double mean) {
        if (values == null || values.size() < 2) {
            return 0;
        }
        double sumSquaredDiffs = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).sum();
        return Math.sqrt(sumSquaredDiffs / (values.size() - 1)); // Sample standard deviation
    }

    /** Internal class to hold statistical data for a sector. */
    private record SectorStats(
            double weeklyMean,
            double weeklyStdDev,
            double monthlyMean,
            double monthlyStdDev,
            int weeklyCount,
            int monthlyCount) {

        boolean hasEnoughData() {
            return weeklyCount >= MIN_HISTORY_SIZE
                    && monthlyCount >= MIN_HISTORY_SIZE
                    && weeklyStdDev > 0
                    && monthlyStdDev > 0;
        }
    }
}
