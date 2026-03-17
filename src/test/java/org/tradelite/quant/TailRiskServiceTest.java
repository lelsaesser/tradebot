package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.repository.PriceQuoteRepository;

@ExtendWith(MockitoExtension.class)
class TailRiskServiceTest {

    @Mock private PriceQuoteRepository priceQuoteRepository;

    private TailRiskService tailRiskService;

    @BeforeEach
    void setUp() {
        tailRiskService = new TailRiskService(priceQuoteRepository);
    }

    @Test
    void analyzeTailRisk_returnsEmptyWhenInsufficientData() {
        when(priceQuoteRepository.findDailyChangePercents("SPY", 35))
                .thenReturn(List.of(1.0, 2.0, -1.0)); // Only 3 data points

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("SPY", "S&P 500");

        assertThat(result).isEmpty();
    }

    @Test
    void analyzeTailRisk_returnsAnalysisWithSufficientData() {
        // 20 data points (minimum required)
        List<Double> returns = generateNormalReturns(20);
        when(priceQuoteRepository.findDailyChangePercents("XLK", 35)).thenReturn(returns);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLK", "Technology");

        assertThat(result).isPresent();
        TailRiskAnalysis analysis = result.get();
        assertThat(analysis.symbol()).isEqualTo("XLK");
        assertThat(analysis.displayName()).isEqualTo("Technology");
        assertThat(analysis.dataPoints()).isEqualTo(20);
        assertThat(analysis.hasReliableData()).isTrue();
    }

    @Test
    void analyzeTailRisk_classifiesNormalDistributionAsLowRisk() {
        // Generate returns that approximate normal distribution (kurtosis ~3)
        List<Double> normalReturns =
                Arrays.asList(
                        -2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, -1.8, 1.8, -0.8, 0.8, -1.2,
                        1.2, -0.3, 0.3, -0.1, 0.1, 0.0);
        when(priceQuoteRepository.findDailyChangePercents("SPY", 35)).thenReturn(normalReturns);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("SPY", "S&P 500");

        assertThat(result).isPresent();
        // Normal distribution should have LOW risk
        assertThat(result.get().riskLevel()).isEqualTo(TailRiskLevel.LOW);
    }

    @Test
    void analyzeTailRisk_classifiesFatTailsAsHighRisk() {
        // Generate returns with fat tails (extreme values)
        List<Double> fatTailReturns =
                Arrays.asList(
                        0.1,
                        0.2,
                        -0.1,
                        0.0,
                        0.1,
                        -0.2,
                        0.1,
                        0.0,
                        -0.1,
                        0.2,
                        0.1,
                        -0.1,
                        0.0,
                        0.1,
                        -0.1,
                        0.0,
                        0.1,
                        -0.2,
                        8.0, // Extreme positive
                        -7.0 // Extreme negative
                        );
        when(priceQuoteRepository.findDailyChangePercents("XLE", 35)).thenReturn(fatTailReturns);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLE", "Energy");

        assertThat(result).isPresent();
        // High kurtosis from extreme values should be HIGH or EXTREME
        TailRiskLevel level = result.get().riskLevel();
        assertThat(level).isIn(TailRiskLevel.HIGH, TailRiskLevel.EXTREME);
    }

    @Test
    void calculateKurtosis_returnsNormalKurtosisForUniformData() {
        // Uniform data should have low kurtosis
        List<Double> uniformReturns = Arrays.asList(1.0, 1.0, 1.0, 1.0, 1.0);

        double kurtosis = tailRiskService.calculateKurtosis(uniformReturns);

        // Zero variance returns normal kurtosis (3.0)
        assertThat(kurtosis).isEqualTo(3.0);
    }

    @Test
    void calculateKurtosis_returnsHigherValueForFatTailData() {
        // Data with extreme outliers
        List<Double> fatTailData =
                Arrays.asList(
                        0.1,
                        0.2,
                        -0.1,
                        0.0,
                        0.1,
                        -0.2,
                        0.1,
                        0.0,
                        -0.1,
                        0.2,
                        0.1,
                        -0.1,
                        0.0,
                        0.1,
                        -0.1,
                        0.0,
                        0.1,
                        -0.2,
                        10.0, // Extreme value
                        -10.0 // Extreme value
                        );

        double kurtosis = tailRiskService.calculateKurtosis(fatTailData);

        // Fat tails should produce kurtosis > 3
        assertThat(kurtosis).isGreaterThan(3.0);
    }

    @Test
    void calculateKurtosis_returnsDefaultForInsufficientData() {
        List<Double> tooFewValues = Arrays.asList(1.0, 2.0, 3.0);

        double kurtosis = tailRiskService.calculateKurtosis(tooFewValues);

        assertThat(kurtosis).isEqualTo(3.0);
    }

    @Test
    void calculateExcessKurtosis_returnsCorrectValue() {
        List<Double> data = Arrays.asList(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0);

        double excessKurtosis = tailRiskService.calculateExcessKurtosis(data);
        double kurtosis = tailRiskService.calculateKurtosis(data);

        assertThat(excessKurtosis).isCloseTo(kurtosis - 3.0, within(0.001));
    }

    // ========== Skewness Tests ==========

    @Test
    void calculateSkewness_returnsZeroForSymmetricData() {
        // Symmetric data around mean
        List<Double> symmetricData =
                Arrays.asList(-3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0,
                        3.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0);

        double skewness = tailRiskService.calculateSkewness(symmetricData);

        // Symmetric data should have skewness close to 0
        assertThat(skewness).isCloseTo(0.0, within(0.1));
    }

    @Test
    void calculateSkewness_returnsNegativeForLeftSkewedData() {
        // Left-skewed: many small positive values, few large negative values (crash-like)
        List<Double> leftSkewedData =
                Arrays.asList(
                        0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4, 0.2, 0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4,
                        0.2, -5.0, // Extreme negative (crash)
                        -6.0, // Extreme negative (crash)
                        -4.5, // Extreme negative
                        0.1);

        double skewness = tailRiskService.calculateSkewness(leftSkewedData);

        // Left-skewed (crash bias) should have negative skewness
        assertThat(skewness).isLessThan(0);
    }

    @Test
    void calculateSkewness_returnsPositiveForRightSkewedData() {
        // Right-skewed: many small negative values, few large positive values (rally-like)
        List<Double> rightSkewedData =
                Arrays.asList(
                        -0.5, -0.3, -0.4, -0.2, -0.1, -0.3, -0.4, -0.2, -0.5, -0.3, -0.4, -0.2,
                        -0.1, -0.3, -0.4, -0.2, 5.0, // Extreme positive (rally)
                        6.0, // Extreme positive (rally)
                        4.5, // Extreme positive
                        -0.1);

        double skewness = tailRiskService.calculateSkewness(rightSkewedData);

        // Right-skewed (rally potential) should have positive skewness
        assertThat(skewness).isGreaterThan(0);
    }

    @Test
    void calculateSkewness_returnsZeroForInsufficientData() {
        List<Double> tooFewValues = Arrays.asList(1.0, 2.0);

        double skewness = tailRiskService.calculateSkewness(tooFewValues);

        assertThat(skewness).isEqualTo(0.0);
    }

    @Test
    void calculateSkewness_returnsZeroForZeroVariance() {
        List<Double> constantValues = Arrays.asList(5.0, 5.0, 5.0, 5.0, 5.0);

        double skewness = tailRiskService.calculateSkewness(constantValues);

        assertThat(skewness).isEqualTo(0.0);
    }

    @Test
    void analyzeTailRisk_includesSkewnessInResult() {
        List<Double> returns = generateNormalReturns(25);
        when(priceQuoteRepository.findDailyChangePercents("XLK", 35)).thenReturn(returns);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLK", "Technology");

        assertThat(result).isPresent();
        TailRiskAnalysis analysis = result.get();
        // Verify skewness fields are present
        assertThat(analysis.skewnessLevel()).isNotNull();
        // Skewness should be a reasonable value
        assertThat(analysis.skewness()).isBetween(-5.0, 5.0);
    }

    @Test
    void analyzeTailRisk_classifiesNegativeSkewnessCorrectly() {
        // Left-skewed data (crash bias)
        List<Double> leftSkewedData =
                Arrays.asList(
                        0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4, 0.2, 0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4,
                        0.2, -5.0, -6.0, -4.5, 0.1);
        when(priceQuoteRepository.findDailyChangePercents("XLE", 35)).thenReturn(leftSkewedData);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLE", "Energy");

        assertThat(result).isPresent();
        assertThat(result.get().skewness()).isLessThan(0);
        assertThat(result.get().skewnessLevel().isNegative()).isTrue();
    }

    @Test
    void analyzeTailRisk_classifiesPositiveSkewnessCorrectly() {
        // Right-skewed data (rally potential)
        List<Double> rightSkewedData =
                Arrays.asList(
                        -0.5, -0.3, -0.4, -0.2, -0.1, -0.3, -0.4, -0.2, -0.5, -0.3, -0.4, -0.2,
                        -0.1, -0.3, -0.4, -0.2, 5.0, 6.0, 4.5, -0.1);
        when(priceQuoteRepository.findDailyChangePercents("XLF", 35)).thenReturn(rightSkewedData);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLF", "Financials");

        assertThat(result).isPresent();
        assertThat(result.get().skewness()).isGreaterThan(0);
        assertThat(result.get().skewnessLevel().isPositive()).isTrue();
    }

    @Test
    void tailRiskAnalysis_hasReliableDataWithMinimumPoints() {
        List<Double> returns = generateNormalReturns(20);
        when(priceQuoteRepository.findDailyChangePercents("XLF", 35)).thenReturn(returns);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLF", "Financials");

        assertThat(result).isPresent();
        assertThat(result.get().hasReliableData()).isTrue();
    }

    @Test
    void tailRiskAnalysis_formatsSummaryLineCorrectly() {
        List<Double> returns = generateNormalReturns(25);
        when(priceQuoteRepository.findDailyChangePercents("XLI", 35)).thenReturn(returns);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLI", "Industrials");

        assertThat(result).isPresent();
        String summaryLine = result.get().toSummaryLine();
        assertThat(summaryLine).contains("XLI").contains("Industrials").contains("Kurtosis");
    }

    @Test
    void tailRiskAnalysis_formatsCompactLineCorrectly() {
        List<Double> returns = generateNormalReturns(25);
        when(priceQuoteRepository.findDailyChangePercents("XLV", 35)).thenReturn(returns);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLV", "Healthcare");

        assertThat(result).isPresent();
        String compactLine = result.get().toCompactLine();
        assertThat(compactLine).contains("XLV");
    }

    private List<Double> generateNormalReturns(int count) {
        Double[] returns = new Double[count];
        for (int i = 0; i < count; i++) {
            // Approximate normal distribution with small returns
            returns[i] = (i % 2 == 0 ? 1 : -1) * (0.1 + (i * 0.1) % 1.5);
        }
        return Arrays.asList(returns);
    }
}
