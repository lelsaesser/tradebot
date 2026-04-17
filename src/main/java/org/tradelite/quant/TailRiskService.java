package org.tradelite.quant;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.repository.PriceQuoteRepository;

/**
 * Service for calculating tail risk metrics using kurtosis and skewness.
 *
 * <p><b>Kurtosis</b> measures the "tailedness" of a probability distribution. Higher kurtosis
 * indicates more extreme values (crashes or rallies) than a normal distribution would predict.
 *
 * <p><b>Skewness</b> measures the asymmetry of the distribution:
 *
 * <ul>
 *   <li>Negative skew: Left tail is fatter → crashes more likely than rallies
 *   <li>Positive skew: Right tail is fatter → rallies more likely than crashes
 *   <li>Zero skew: Symmetric distribution → balanced risk
 * </ul>
 *
 * <p>Normal distribution has kurtosis = 3.0 (mesokurtic) and skewness = 0.0. Values above 3
 * indicate fat tails (leptokurtic), meaning extreme price moves are more likely than normal.
 *
 * <p><b>Stock split safety:</b> This service operates on daily change percents (via {@link
 * PriceQuoteRepository#findDailyChangePercents}), not absolute prices. Percentage changes are
 * invariant to stock splits — a 2% daily move is 2% regardless of pre- or post-split price levels.
 * Therefore, no data reset is needed for this service when a stock split occurs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TailRiskService {

    /** Minimum number of data points required for kurtosis/skewness calculation. */
    private static final int MIN_DATA_POINTS = 20;

    /** Number of calendar days to look back for price data. */
    private static final int LOOKBACK_DAYS = 35;

    private final PriceQuoteRepository priceQuoteRepository;

    /**
     * Analyzes tail risk for a symbol based on historical price data.
     *
     * @param symbol The stock or ETF ticker symbol
     * @param displayName Human-readable name for the symbol
     * @return Optional containing analysis results, empty if insufficient data
     */
    public Optional<TailRiskAnalysis> analyzeTailRisk(String symbol, String displayName) {
        List<Double> returns = priceQuoteRepository.findDailyChangePercents(symbol, LOOKBACK_DAYS);

        if (returns.size() < MIN_DATA_POINTS) {
            return Optional.empty();
        }

        double kurtosis = calculateKurtosis(returns);
        double excessKurtosis = kurtosis - 3.0;
        TailRiskLevel riskLevel = TailRiskLevel.fromExcessKurtosis(excessKurtosis);

        double skewness = calculateSkewness(returns);
        SkewnessLevel skewnessLevel = SkewnessLevel.fromSkewness(skewness);

        return Optional.of(
                new TailRiskAnalysis(
                        symbol,
                        displayName,
                        kurtosis,
                        excessKurtosis,
                        riskLevel,
                        skewness,
                        skewnessLevel,
                        returns.size()));
    }

    /**
     * Calculates the kurtosis of a distribution.
     *
     * <p>Kurtosis formula: E[(X - μ)^4] / σ^4
     *
     * <p>For sample kurtosis, we use the formula: n * Σ((xi - x̄)^4) / (Σ((xi - x̄)^2))^2
     *
     * @param values List of values (e.g., daily returns)
     * @return The calculated kurtosis (normal distribution = 3.0)
     */
    protected double calculateKurtosis(List<Double> values) {
        if (values.size() < 4) {
            return 3.0; // Return normal kurtosis if insufficient data
        }

        int n = values.size();
        double mean = StatisticsUtil.mean(values);

        // Calculate second and fourth central moments
        double sumSquaredDiff = 0;
        double sumFourthPower = 0;

        for (Double value : values) {
            double diff = value - mean;
            double squaredDiff = diff * diff;
            sumSquaredDiff += squaredDiff;
            sumFourthPower += squaredDiff * squaredDiff;
        }

        if (sumSquaredDiff == 0) {
            return 3.0; // No variance, return normal kurtosis
        }

        // Sample kurtosis using the formula: n * Σ(xi - x̄)^4 / (Σ(xi - x̄)^2)^2
        return (n * sumFourthPower) / (sumSquaredDiff * sumSquaredDiff);
    }

    /**
     * Calculates excess kurtosis (kurtosis - 3).
     *
     * <p>Excess kurtosis is often more intuitive as it measures deviation from normal distribution:
     *
     * <ul>
     *   <li>Positive = fatter tails than normal (leptokurtic)
     *   <li>Negative = thinner tails than normal (platykurtic)
     *   <li>Zero = normal distribution (mesokurtic)
     * </ul>
     *
     * @param values List of values
     * @return Excess kurtosis
     */
    protected double calculateExcessKurtosis(List<Double> values) {
        return calculateKurtosis(values) - 3.0;
    }

    /**
     * Calculates the skewness of a distribution.
     *
     * <p>Skewness formula: E[(X - μ)^3] / σ^3
     *
     * <p>For sample skewness, we use: Σ((xi - x̄)^3) / (n * σ^3)
     *
     * <p>Interpretation:
     *
     * <ul>
     *   <li>Negative skew (< 0): Left tail is longer/fatter → more crash risk
     *   <li>Zero skew (= 0): Symmetric distribution
     *   <li>Positive skew (> 0): Right tail is longer/fatter → more rally potential
     * </ul>
     *
     * @param values List of values (e.g., daily returns)
     * @return The calculated skewness (normal distribution = 0.0)
     */
    protected double calculateSkewness(List<Double> values) {
        if (values.size() < 3) {
            return 0.0; // Return zero skewness if insufficient data
        }

        int n = values.size();
        double mean = StatisticsUtil.mean(values);

        // Calculate sum of squared differences and cubed differences
        double sumSquaredDiff = 0;
        double sumCubedDiff = 0;

        for (Double value : values) {
            double diff = value - mean;
            double squaredDiff = diff * diff;
            sumSquaredDiff += squaredDiff;
            sumCubedDiff += squaredDiff * diff; // diff^3
        }

        if (sumSquaredDiff == 0) {
            return 0.0; // No variance, return zero skewness
        }

        // Calculate standard deviation
        double variance = sumSquaredDiff / n;
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) {
            return 0.0;
        }

        // Sample skewness: (1/n) * Σ((xi - x̄)^3) / σ^3
        double stdDevCubed = stdDev * stdDev * stdDev;
        return (sumCubedDiff / n) / stdDevCubed;
    }
}
