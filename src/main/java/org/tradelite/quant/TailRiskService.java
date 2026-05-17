package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.service.DailyPriceProvider;
import org.tradelite.service.model.DailyPrice;

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
 * <p><b>Stock split safety:</b> This service derives daily change percents from consecutive closing
 * prices provided by {@link DailyPriceProvider}, which returns split-adjusted OHLCV data.
 * Percentage changes computed from split-adjusted prices are invariant to stock splits — a 2% daily
 * move is 2% regardless of pre- or post-split price levels. Therefore, no data reset is needed for
 * this service when a stock split occurs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TailRiskService {

    /** Minimum number of data points required for kurtosis/skewness calculation. */
    private static final int MIN_DATA_POINTS = 20;

    /** Number of calendar days to look back for price data. */
    private static final int LOOKBACK_DAYS = 35;

    private final DailyPriceProvider dailyPriceProvider;

    /**
     * Analyzes tail risk for a symbol based on historical price data.
     *
     * @param symbol The stock or ETF ticker symbol
     * @param displayName Human-readable name for the symbol
     * @return Optional containing analysis results, empty if insufficient data
     */
    public Optional<TailRiskAnalysis> analyzeTailRisk(String symbol, String displayName) {
        List<DailyPrice> dailyPrices =
                dailyPriceProvider.findDailyClosingPrices(symbol, LOOKBACK_DAYS);
        List<Double> returns = toDailyChangePercents(dailyPrices);

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

    protected List<Double> toDailyChangePercents(List<DailyPrice> prices) {
        List<Double> changePercents = new ArrayList<>();
        for (int i = 1; i < prices.size(); i++) {
            double prevPrice = prices.get(i - 1).getPrice();
            if (prevPrice != 0) {
                double currPrice = prices.get(i).getPrice();
                changePercents.add(((currPrice - prevPrice) / prevPrice) * 100);
            }
        }
        return changePercents;
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
