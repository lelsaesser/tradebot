package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.statistics.descriptive.Kurtosis;
import org.apache.commons.statistics.descriptive.Skewness;
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
 * <p><b>Lookback window:</b> the lookback is a {@link TailRiskWindow} parameter on {@link
 * #analyzeTailRisk}. Canonical instances used by {@link TailRiskTracker} are:
 *
 * <ul>
 *   <li>{@code SHORT} — 25 trading days (~1 month) — short window, responsive but biased downward
 *       on kurtosis
 *   <li>{@code LONG} — 252 trading days (~1 year) — long window, matches Quantpedia / Basel-FRTB
 *       practice and is stable enough that sample kurtosis approximates the population value
 * </ul>
 *
 * <p>Both windows are run in parallel during the comparison period so production data can decide
 * which estimator is preferred. See issue #336 for context.
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

    private final DailyPriceProvider dailyPriceProvider;

    /**
     * Symbols for which the "insufficient price history" WARN log is suppressed.
     *
     * <p><b>Why:</b> these are newly-listed instruments whose history is legitimately shorter than
     * the {@link TailRiskTracker#LONG} 252-trading-day window requires. The warn fires every run
     * and pollutes {@code ./scan-logs.sh} output for ~200+ days while the symbol matures.
     *
     * <p><b>When to remove:</b> once the symbol has ≥ 252 rows of price history. See issue #494.
     */
    private static final Set<String> SUPPRESS_INSUFFICIENT_DATA_WARN = Set.of("DRAM");

    /**
     * Analyzes tail risk for a symbol based on historical price data.
     *
     * @param symbol The stock or ETF ticker symbol
     * @param displayName Human-readable name for the symbol
     * @param window Window spec coupling the calendar lookback (used to fetch price data) with the
     *     minimum row count required for the analysis to be valid.
     * @return Optional containing analysis results, empty if fewer than {@code
     *     window.minDataPoints()} prices are available
     */
    public Optional<TailRiskAnalysis> analyzeTailRisk(
            String symbol, String displayName, TailRiskWindow window) {
        List<DailyPrice> dailyPrices =
                dailyPriceProvider.findDailyClosingPrices(symbol, window.lookbackCalendarDays());

        if (dailyPrices.size() < window.minDataPoints()) {
            if (!SUPPRESS_INSUFFICIENT_DATA_WARN.contains(symbol)) {
                log.warn(
                        "Insufficient price history for {}: have {} rows, need {}",
                        symbol,
                        dailyPrices.size(),
                        window.minDataPoints());
            }
            return Optional.empty();
        }

        // Truncate to exactly minDataPoints (latest rows) so the sample size is stable across runs.
        // Kurtosis and skewness estimators are sensitive to sample size; fixing it makes
        // day-over-day comparisons meaningful.
        List<DailyPrice> sample =
                dailyPrices.subList(
                        dailyPrices.size() - window.minDataPoints(), dailyPrices.size());

        List<Double> returns = toDailyChangePercents(sample);

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
     * Calculates the kurtosis of a distribution using the bias-corrected G2 (Fisher-Pearson)
     * estimator — the industry standard in quantitative finance, matching what Pandas, NumPy (with
     * {@code bias=False}), Excel {@code KURT}, R {@code e1071::kurtosis(type=2)}, and the
     * Basel-FRTB framework return.
     *
     * <p>Returns the raw kurtosis (normal distribution = 3.0). The G2 estimator unbiased-ly
     * estimates the population kurtosis from a sample; this is materially different from the biased
     * method-of-moments formula at small sample sizes (~8% upward shift at n=25). The biased
     * formula was used prior to #433 and systematically under-read tail risk.
     *
     * <p>Implementation delegates to Apache Commons Statistics' {@link Kurtosis}, which returns
     * <em>excess</em> kurtosis (normal = 0); we add 3.0 to preserve the raw-kurtosis contract used
     * at call sites in {@link #analyzeTailRisk}.
     *
     * <p>Size and zero-variance guards are kept as safety nets: Commons Statistics returns {@code
     * NaN} for n &lt; 4 or zero variance, and downstream {@link TailRiskLevel#fromExcessKurtosis}
     * misclassifies {@code NaN} as {@code EXTREME} (because {@code NaN < 1.0} is false → falls
     * through every comparison branch). The guards short-circuit before Commons Statistics is
     * called and return the normal-distribution sentinel (3.0) — the right semantic for
     * "insufficient data" or "perfectly flat series."
     *
     * @param values List of values (e.g., daily returns)
     * @return Raw kurtosis (normal distribution = 3.0); returns 3.0 as sentinel for {@code n < 4}
     *     or zero-variance input
     * @see <a href="https://github.com/lelsaesser/tradebot/issues/433">#433</a>
     */
    protected double calculateKurtosis(List<Double> values) {
        if (values.size() < 4) {
            return 3.0; // Defensive guard for direct test calls; never fires in production.
        }
        if (hasZeroVariance(values)) {
            return 3.0; // Flat series has no kurtosis — return normal-distribution sentinel.
        }
        // Commons Statistics' Kurtosis is G2 (Fisher-Pearson, bias-corrected) and returns excess
        // kurtosis. Add 3.0 to preserve the raw-kurtosis (normal=3) contract used at call sites.
        return Kurtosis.of(toArray(values)).getAsDouble() + 3.0;
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
     * Calculates the skewness of a distribution using the bias-corrected G1 (Fisher-Pearson)
     * estimator — the industry standard in quantitative finance, matching what Pandas, NumPy (with
     * {@code bias=False}), Excel {@code SKEW}, and R {@code e1071::skewness(type=2)} return.
     *
     * <p>Returns skewness with the textbook convention (normal distribution = 0; negative = left
     * tail / crash bias; positive = right tail / rally bias). At small sample sizes the G1
     * estimator yields larger magnitudes than the biased method-of-moments formula (~15% larger at
     * n=25). The biased formula was used prior to #433 and systematically under-read directional
     * skew.
     *
     * <p>Implementation delegates to Apache Commons Statistics' {@link Skewness}.
     *
     * <p>Size and zero-variance guards are kept as safety nets: Commons Statistics returns {@code
     * NaN} for n &lt; 3 or zero variance, and downstream {@link SkewnessLevel#fromSkewness} would
     * misclassify NaN. The guards short-circuit before Commons Statistics is called and return 0.0
     * — the right semantic for "insufficient data" or "perfectly flat series."
     *
     * @param values List of values (e.g., daily returns)
     * @return Skewness (normal distribution = 0.0); returns 0.0 as sentinel for {@code n < 3} or
     *     zero-variance input
     * @see <a href="https://github.com/lelsaesser/tradebot/issues/433">#433</a>
     */
    protected double calculateSkewness(List<Double> values) {
        if (values.size() < 3) {
            return 0.0;
        }
        if (hasZeroVariance(values)) {
            return 0.0;
        }
        // Commons Statistics' Skewness is G1 (Fisher-Pearson, bias-corrected).
        return Skewness.of(toArray(values)).getAsDouble();
    }

    /**
     * Returns {@code true} if every value is identical (within exact double equality of the mean).
     * Used to short-circuit Commons Statistics, which returns {@code NaN} for zero-variance input.
     * Equivalent to checking {@code Σ(xᵢ − x̄)² == 0}.
     */
    private static boolean hasZeroVariance(List<Double> values) {
        double mean = StatisticsUtil.mean(values);
        for (Double v : values) {
            if (v != mean) {
                return false;
            }
        }
        return true;
    }

    /** Stream-based conversion used at the {@code List<Double>} → Commons Statistics boundary. */
    private static double[] toArray(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
