package org.tradelite.quant;

import java.util.List;

/**
 * Shared statistical utility methods used across quantitative analysis features.
 *
 * <p>Consolidates common calculations (mean, standard deviation, z-score, percentile).
 *
 * <p>All methods are static and stateless — this is a pure utility class.
 */
public final class StatisticsUtil {

    private StatisticsUtil() {}

    /**
     * Calculates the arithmetic mean of a list of values.
     *
     * @param values The values to average
     * @return The mean, or 0 if values is null or empty
     */
    public static double mean(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /**
     * Calculates the arithmetic mean for a subrange of a list.
     *
     * @param values Full value list
     * @param fromIndex Start index (inclusive)
     * @param toIndex End index (exclusive)
     * @return The mean for the specified range
     */
    public static double mean(List<Double> values, int fromIndex, int toIndex) {
        double sum = 0;
        for (int i = fromIndex; i < toIndex; i++) {
            sum += values.get(i);
        }
        return sum / (toIndex - fromIndex);
    }

    /**
     * Calculates the population standard deviation (divides by n).
     *
     * <p>Used when the data represents the entire population of interest (e.g., a fixed rolling
     * window of prices for Bollinger Bands or tail risk).
     *
     * @param values The values
     * @param mean Precomputed mean
     * @return Population standard deviation, or 0 if insufficient data
     */
    public static double populationStdDev(List<Double> values, double mean) {
        if (values == null || values.isEmpty()) {
            return 0;
        }
        double sumSquaredDiffs = 0;
        for (Double v : values) {
            double diff = v - mean;
            sumSquaredDiffs += diff * diff;
        }
        return Math.sqrt(sumSquaredDiffs / values.size());
    }

    /**
     * Calculates the population standard deviation for a subrange of a list.
     *
     * @param values Full value list
     * @param fromIndex Start index (inclusive)
     * @param toIndex End index (exclusive)
     * @param mean Precomputed mean for this range
     * @return Population standard deviation for the specified range
     */
    public static double populationStdDev(
            List<Double> values, int fromIndex, int toIndex, double mean) {
        double sumSquaredDiffs = 0;
        for (int i = fromIndex; i < toIndex; i++) {
            double diff = values.get(i) - mean;
            sumSquaredDiffs += diff * diff;
        }
        return Math.sqrt(sumSquaredDiffs / (toIndex - fromIndex));
    }

    /**
     * Calculates the sample standard deviation (divides by n-1, Bessel's correction).
     *
     * <p>Used when the data represents a sample from a larger population (e.g., historical sector
     * performance snapshots estimating true sector volatility).
     *
     * @param values The values
     * @param mean Precomputed mean
     * @return Sample standard deviation, or 0 if fewer than 2 values
     */
    public static double sampleStdDev(List<Double> values, double mean) {
        if (values == null || values.size() < 2) {
            return 0;
        }
        double sumSquaredDiffs = 0;
        for (Double v : values) {
            double diff = v - mean;
            sumSquaredDiffs += diff * diff;
        }
        return Math.sqrt(sumSquaredDiffs / (values.size() - 1));
    }

    /**
     * Calculates the z-score: (value - mean) / stdDev.
     *
     * @param value The value to normalize
     * @param mean The distribution mean
     * @param stdDev The standard deviation
     * @return z-score, or 0 if stdDev is 0
     */
    public static double zScore(double value, double mean, double stdDev) {
        if (stdDev == 0) {
            return 0;
        }
        return (value - mean) / stdDev;
    }

    /**
     * Calculates the percentile rank of a value within a distribution.
     *
     * <p>Returns the percentage of values in the distribution that are less than the current value.
     *
     * @param values Historical values forming the distribution
     * @param currentValue The value to rank
     * @return Percentile rank (0-100), or 50.0 if distribution is empty
     */
    public static double percentile(List<Double> values, double currentValue) {
        if (values == null || values.isEmpty()) {
            return 50.0;
        }
        long countBelow = values.stream().filter(v -> v < currentValue).count();
        return (countBelow * 100.0) / values.size();
    }
}
