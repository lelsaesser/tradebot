package org.tradelite.quant;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.repository.PriceQuoteRepository;

/**
 * Service for calculating tail risk metrics using kurtosis.
 *
 * <p>Kurtosis measures the "tailedness" of a probability distribution. Higher kurtosis indicates
 * more extreme values (crashes or rallies) than a normal distribution would predict.
 *
 * <p>Normal distribution has kurtosis = 3.0 (mesokurtic). Values above 3 indicate fat tails
 * (leptokurtic), meaning extreme price moves are more likely than normal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TailRiskService {

    /** Minimum number of data points required for kurtosis calculation. */
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

        return Optional.of(
                new TailRiskAnalysis(
                        symbol, displayName, kurtosis, excessKurtosis, riskLevel, returns.size()));
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

        // Calculate mean
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);

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
     * - Positive = fatter tails than normal (leptokurtic) - Negative = thinner tails than normal
     * (platykurtic) - Zero = normal distribution (mesokurtic)
     *
     * @param values List of values
     * @return Excess kurtosis
     */
    protected double calculateExcessKurtosis(List<Double> values) {
        return calculateKurtosis(values) - 3.0;
    }
}
