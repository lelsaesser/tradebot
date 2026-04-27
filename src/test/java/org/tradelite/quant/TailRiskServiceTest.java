package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.service.DailyPriceProvider;
import org.tradelite.service.model.DailyPrice;

@ExtendWith(MockitoExtension.class)
class TailRiskServiceTest {

    @Mock private DailyPriceProvider dailyPriceProvider;

    private TailRiskService tailRiskService;

    @BeforeEach
    void setUp() {
        tailRiskService = new TailRiskService(dailyPriceProvider);
    }

    @Test
    void analyzeTailRisk_returnsEmptyWhenInsufficientData() {
        // Only 3 change percents → need 4 prices
        List<DailyPrice> prices = toDailyPrices(100.0, 101.0, 103.0, 102.0);
        when(dailyPriceProvider.findDailyClosingPrices("SPY", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("SPY", "S&P 500");

        assertThat(result).isEmpty();
    }

    @Test
    void analyzeTailRisk_returnsAnalysisWithSufficientData() {
        // 21 prices → 20 change percents (minimum required)
        List<DailyPrice> prices = changesToPrices(generateNormalReturns(20));
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

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
        List<Double> normalReturns =
                Arrays.asList(
                        -2.0, -1.5, -1.0, -0.5, 0.0, 0.5, 1.0, 1.5, 2.0, -1.8, 1.8, -0.8, 0.8, -1.2,
                        1.2, -0.3, 0.3, -0.1, 0.1, 0.0);
        List<DailyPrice> prices = changesToPrices(normalReturns);
        when(dailyPriceProvider.findDailyClosingPrices("SPY", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("SPY", "S&P 500");

        assertThat(result).isPresent();
        assertThat(result.get().riskLevel()).isEqualTo(TailRiskLevel.LOW);
    }

    @Test
    void analyzeTailRisk_classifiesFatTailsAsHighRisk() {
        List<Double> fatTailReturns =
                Arrays.asList(
                        0.1, 0.2, -0.1, 0.0, 0.1, -0.2, 0.1, 0.0, -0.1, 0.2, 0.1, -0.1, 0.0, 0.1,
                        -0.1, 0.0, 0.1, -0.2, 8.0, -7.0);
        List<DailyPrice> prices = changesToPrices(fatTailReturns);
        when(dailyPriceProvider.findDailyClosingPrices("XLE", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLE", "Energy");

        assertThat(result).isPresent();
        TailRiskLevel level = result.get().riskLevel();
        assertThat(level).isIn(TailRiskLevel.HIGH, TailRiskLevel.EXTREME);
    }

    @Test
    void calculateKurtosis_returnsNormalKurtosisForUniformData() {
        List<Double> uniformReturns = Arrays.asList(1.0, 1.0, 1.0, 1.0, 1.0);

        double kurtosis = tailRiskService.calculateKurtosis(uniformReturns);

        assertThat(kurtosis).isEqualTo(3.0);
    }

    @Test
    void calculateKurtosis_returnsHigherValueForFatTailData() {
        List<Double> fatTailData =
                Arrays.asList(
                        0.1, 0.2, -0.1, 0.0, 0.1, -0.2, 0.1, 0.0, -0.1, 0.2, 0.1, -0.1, 0.0, 0.1,
                        -0.1, 0.0, 0.1, -0.2, 10.0, -10.0);

        double kurtosis = tailRiskService.calculateKurtosis(fatTailData);

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
        List<Double> symmetricData =
                Arrays.asList(
                        -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0, -3.0, -2.0, -1.0, 0.0, 1.0, 2.0, 3.0,
                        -3.0, -2.0, -1.0, 0.0, 1.0, 2.0);

        double skewness = tailRiskService.calculateSkewness(symmetricData);

        assertThat(skewness).isCloseTo(0.0, within(0.1));
    }

    @Test
    void calculateSkewness_returnsNegativeForLeftSkewedData() {
        List<Double> leftSkewedData =
                Arrays.asList(
                        0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4, 0.2, 0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4,
                        0.2, -5.0, -6.0, -4.5, 0.1);

        double skewness = tailRiskService.calculateSkewness(leftSkewedData);

        assertThat(skewness).isLessThan(0);
    }

    @Test
    void calculateSkewness_returnsPositiveForRightSkewedData() {
        List<Double> rightSkewedData =
                Arrays.asList(
                        -0.5, -0.3, -0.4, -0.2, -0.1, -0.3, -0.4, -0.2, -0.5, -0.3, -0.4, -0.2,
                        -0.1, -0.3, -0.4, -0.2, 5.0, 6.0, 4.5, -0.1);

        double skewness = tailRiskService.calculateSkewness(rightSkewedData);

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
        List<DailyPrice> prices = changesToPrices(generateNormalReturns(25));
        when(dailyPriceProvider.findDailyClosingPrices("XLK", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLK", "Technology");

        assertThat(result).isPresent();
        TailRiskAnalysis analysis = result.get();
        assertThat(analysis.skewnessLevel()).isNotNull();
        assertThat(analysis.skewness()).isBetween(-5.0, 5.0);
    }

    @Test
    void analyzeTailRisk_classifiesNegativeSkewnessCorrectly() {
        List<Double> leftSkewedData =
                Arrays.asList(
                        0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4, 0.2, 0.5, 0.3, 0.4, 0.2, 0.1, 0.3, 0.4,
                        0.2, -5.0, -6.0, -4.5, 0.1);
        List<DailyPrice> prices = changesToPrices(leftSkewedData);
        when(dailyPriceProvider.findDailyClosingPrices("XLE", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLE", "Energy");

        assertThat(result).isPresent();
        assertThat(result.get().skewness()).isLessThan(0);
        assertThat(result.get().skewnessLevel().isNegative()).isTrue();
    }

    @Test
    void analyzeTailRisk_classifiesPositiveSkewnessCorrectly() {
        List<Double> rightSkewedData =
                Arrays.asList(
                        -0.5, -0.3, -0.4, -0.2, -0.1, -0.3, -0.4, -0.2, -0.5, -0.3, -0.4, -0.2,
                        -0.1, -0.3, -0.4, -0.2, 5.0, 6.0, 4.5, -0.1);
        List<DailyPrice> prices = changesToPrices(rightSkewedData);
        when(dailyPriceProvider.findDailyClosingPrices("XLF", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLF", "Financials");

        assertThat(result).isPresent();
        assertThat(result.get().skewness()).isGreaterThan(0);
        assertThat(result.get().skewnessLevel().isPositive()).isTrue();
    }

    @Test
    void tailRiskAnalysis_hasReliableDataWithMinimumPoints() {
        List<DailyPrice> prices = changesToPrices(generateNormalReturns(20));
        when(dailyPriceProvider.findDailyClosingPrices("XLF", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLF", "Financials");

        assertThat(result).isPresent();
        assertThat(result.get().hasReliableData()).isTrue();
    }

    @Test
    void tailRiskAnalysis_formatsSummaryLineCorrectly() {
        List<DailyPrice> prices = changesToPrices(generateNormalReturns(25));
        when(dailyPriceProvider.findDailyClosingPrices("XLI", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLI", "Industrials");

        assertThat(result).isPresent();
        String summaryLine = result.get().toSummaryLine();
        assertThat(summaryLine).contains("XLI").contains("Industrials").contains("Kurtosis");
    }

    @Test
    void tailRiskAnalysis_formatsCompactLineCorrectly() {
        List<DailyPrice> prices = changesToPrices(generateNormalReturns(25));
        when(dailyPriceProvider.findDailyClosingPrices("XLV", 35)).thenReturn(prices);

        Optional<TailRiskAnalysis> result = tailRiskService.analyzeTailRisk("XLV", "Healthcare");

        assertThat(result).isPresent();
        String compactLine = result.get().toCompactLine();
        assertThat(compactLine).contains("XLV");
    }

    // ========== Price-to-change-percent conversion tests ==========

    @Test
    void toDailyChangePercents_computesCorrectValues() {
        List<DailyPrice> prices = toDailyPrices(100.0, 102.0, 101.0, 105.05);

        List<Double> changePercents = tailRiskService.toDailyChangePercents(prices);

        assertThat(changePercents).hasSize(3);
        assertThat(changePercents.get(0)).isCloseTo(2.0, within(0.001));
        assertThat(changePercents.get(1)).isCloseTo(-0.9804, within(0.001));
        assertThat(changePercents.get(2)).isCloseTo(4.0099, within(0.001));
    }

    @Test
    void toDailyChangePercents_returnsEmptyForSinglePrice() {
        List<DailyPrice> prices = toDailyPrices(100.0);

        List<Double> changePercents = tailRiskService.toDailyChangePercents(prices);

        assertThat(changePercents).isEmpty();
    }

    @Test
    void toDailyChangePercents_returnsEmptyForEmptyList() {
        List<Double> changePercents = tailRiskService.toDailyChangePercents(List.of());

        assertThat(changePercents).isEmpty();
    }

    @Test
    void toDailyChangePercents_skipsZeroPreviousPrice() {
        List<DailyPrice> prices = toDailyPrices(0.0, 100.0, 102.0);

        List<Double> changePercents = tailRiskService.toDailyChangePercents(prices);

        // First change (0 → 100) skipped, only 100 → 102 remains
        assertThat(changePercents).hasSize(1);
        assertThat(changePercents.get(0)).isCloseTo(2.0, within(0.001));
    }

    // ========== Helpers ==========

    private List<Double> generateNormalReturns(int count) {
        Double[] returns = new Double[count];
        for (int i = 0; i < count; i++) {
            returns[i] = (i % 2 == 0 ? 1 : -1) * (0.1 + (i * 0.1) % 1.5);
        }
        return Arrays.asList(returns);
    }

    /** Creates a DailyPrice list from raw closing prices, with sequential dates. */
    private List<DailyPrice> toDailyPrices(double... closingPrices) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (double price : closingPrices) {
            prices.add(new DailyPrice(date, price));
            date = date.plusDays(1);
        }
        return prices;
    }

    /**
     * Converts a list of daily change percents into a DailyPrice list by building a price series
     * starting at 100.0. This allows reusing existing change-percent test data.
     */
    private List<DailyPrice> changesToPrices(List<Double> changePercents) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        double price = 100.0;
        prices.add(new DailyPrice(date, price));
        for (Double changePct : changePercents) {
            date = date.plusDays(1);
            price = price * (1.0 + changePct / 100.0);
            prices.add(new DailyPrice(date, price));
        }
        return prices;
    }
}
