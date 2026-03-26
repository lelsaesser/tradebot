package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

@ExtendWith(MockitoExtension.class)
class BollingerBandServiceTest {

    @Mock private PriceQuoteRepository priceQuoteRepository;

    private BollingerBandService service;

    @BeforeEach
    void setUp() {
        service = new BollingerBandService(priceQuoteRepository);
    }

    // ========== analyze() integration tests ==========

    @Test
    void analyze_returnsEmptyWhenInsufficientData() {
        List<DailyPrice> prices = generateDailyPrices(10, 100.0, 0.5);
        when(priceQuoteRepository.findDailyClosingPrices("SPY", 90)).thenReturn(prices);

        Optional<BollingerBandAnalysis> result = service.analyze("SPY", "S&P 500");

        assertThat(result).isEmpty();
    }

    @Test
    void analyze_returnsAnalysisWithSufficientData() {
        List<DailyPrice> prices = generateDailyPrices(50, 100.0, 0.5);
        when(priceQuoteRepository.findDailyClosingPrices("XLK", 90)).thenReturn(prices);

        Optional<BollingerBandAnalysis> result = service.analyze("XLK", "Technology");

        assertThat(result).isPresent();
        BollingerBandAnalysis analysis = result.get();
        assertThat(analysis.symbol()).isEqualTo("XLK");
        assertThat(analysis.displayName()).isEqualTo("Technology");
        assertThat(analysis.dataPoints()).isEqualTo(50);
        assertThat(analysis.hasReliableData()).isTrue();
    }

    // ========== calculateBollingerBands() core logic tests ==========

    @Test
    void calculateBollingerBands_computesCorrectSmaForConstantPrices() {
        // 40 constant prices at 100.0
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            prices.add(100.0);
        }

        BollingerBandAnalysis result = service.calculateBollingerBands("SPY", "S&P 500", prices);

        assertThat(result.sma()).isCloseTo(100.0, within(0.001));
        assertThat(result.currentPrice()).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculateBollingerBands_upperBandAboveSma() {
        List<Double> prices = generatePriceList(50, 100.0, 1.0);

        BollingerBandAnalysis result = service.calculateBollingerBands("XLK", "Tech", prices);

        assertThat(result.upperBand()).isGreaterThan(result.sma());
        assertThat(result.lowerBand()).isLessThan(result.sma());
    }

    @Test
    void calculateBollingerBands_bandwidthIsPositiveForVaryingPrices() {
        List<Double> prices = generatePriceList(50, 100.0, 2.0);

        BollingerBandAnalysis result = service.calculateBollingerBands("XLE", "Energy", prices);

        assertThat(result.bandwidth()).isGreaterThan(0);
    }

    @Test
    void calculateBollingerBands_bandwidthIsZeroForConstantPrices() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            prices.add(50.0);
        }

        BollingerBandAnalysis result = service.calculateBollingerBands("XLF", "Financials", prices);

        assertThat(result.bandwidth()).isCloseTo(0.0, within(0.001));
        // Upper and lower bands should equal SMA when no volatility
        assertThat(result.upperBand()).isCloseTo(result.sma(), within(0.001));
        assertThat(result.lowerBand()).isCloseTo(result.sma(), within(0.001));
    }

    @Test
    void calculateBollingerBands_percentBMidpointForConstantPrices() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            prices.add(100.0);
        }

        BollingerBandAnalysis result = service.calculateBollingerBands("SPY", "S&P 500", prices);

        // With zero bandwidth, %B defaults to 0.5
        assertThat(result.percentB()).isCloseTo(0.5, within(0.001));
    }

    @Test
    void calculateBollingerBands_percentBAboveOneWhenPriceAboveUpperBand() {
        // Create prices that are stable then spike at the end
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 39; i++) {
            prices.add(100.0 + (i % 2 == 0 ? 0.5 : -0.5)); // Low volatility around 100
        }
        prices.add(120.0); // Sharp spike — should be well above upper band

        BollingerBandAnalysis result = service.calculateBollingerBands("XLE", "Energy", prices);

        assertThat(result.percentB()).isGreaterThanOrEqualTo(1.0);
        assertThat(result.signals()).contains(BollingerSignalType.UPPER_BAND_TOUCH);
    }

    @Test
    void calculateBollingerBands_percentBBelowZeroWhenPriceBelowLowerBand() {
        // Create prices that are stable then drop sharply
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 39; i++) {
            prices.add(100.0 + (i % 2 == 0 ? 0.5 : -0.5));
        }
        prices.add(80.0); // Sharp drop — should be below lower band

        BollingerBandAnalysis result = service.calculateBollingerBands("XLV", "Healthcare", prices);

        assertThat(result.percentB()).isLessThanOrEqualTo(0.0);
        assertThat(result.signals()).contains(BollingerSignalType.LOWER_BAND_TOUCH);
    }

    // ========== Signal detection tests ==========

    @Test
    void detectSignals_upperBandTouchWhenPercentBAboveOne() {
        List<BollingerSignalType> signals = BollingerBandService.detectSignals(1.05, 50.0);

        assertThat(signals).containsExactly(BollingerSignalType.UPPER_BAND_TOUCH);
    }

    @Test
    void detectSignals_lowerBandTouchWhenPercentBBelowZero() {
        List<BollingerSignalType> signals = BollingerBandService.detectSignals(-0.05, 50.0);

        assertThat(signals).containsExactly(BollingerSignalType.LOWER_BAND_TOUCH);
    }

    @Test
    void detectSignals_squeezeWhenBandwidthPercentileBelowThreshold() {
        List<BollingerSignalType> signals = BollingerBandService.detectSignals(0.5, 5.0);

        assertThat(signals).containsExactly(BollingerSignalType.SQUEEZE);
    }

    @Test
    void detectSignals_multipleSignalsWhenUpperTouchAndSqueeze() {
        List<BollingerSignalType> signals = BollingerBandService.detectSignals(1.2, 3.0);

        assertThat(signals)
                .containsExactly(BollingerSignalType.UPPER_BAND_TOUCH, BollingerSignalType.SQUEEZE);
    }

    @Test
    void detectSignals_emptyWhenNoSignalConditionsMet() {
        List<BollingerSignalType> signals = BollingerBandService.detectSignals(0.5, 50.0);

        assertThat(signals).isEmpty();
    }

    @Test
    void detectSignals_noSignalAtExactBoundaries() {
        // %B exactly at 0.0 should trigger lower band touch
        List<BollingerSignalType> signals = BollingerBandService.detectSignals(0.0, 50.0);
        assertThat(signals).containsExactly(BollingerSignalType.LOWER_BAND_TOUCH);

        // %B exactly at 1.0 should trigger upper band touch
        signals = BollingerBandService.detectSignals(1.0, 50.0);
        assertThat(signals).containsExactly(BollingerSignalType.UPPER_BAND_TOUCH);

        // Bandwidth percentile exactly at threshold should trigger squeeze
        signals = BollingerBandService.detectSignals(0.5, 10.0);
        assertThat(signals).containsExactly(BollingerSignalType.SQUEEZE);
    }

    // ========== Bandwidth history tests ==========

    @Test
    void calculateBandwidthHistory_returnsCorrectNumberOfEntries() {
        List<Double> prices = generatePriceList(50, 100.0, 1.0);

        List<Double> history = service.calculateBandwidthHistory(prices);

        // Should have (n - PERIOD + 1) entries: from window [0,20) to [30,50)
        assertThat(history).hasSize(50 - BollingerBandService.PERIOD + 1);
    }

    @Test
    void calculateBandwidthHistory_allPositiveForVaryingPrices() {
        List<Double> prices = generatePriceList(50, 100.0, 2.0);

        List<Double> history = service.calculateBandwidthHistory(prices);

        assertThat(history).allMatch(bw -> bw > 0);
    }

    @Test
    void calculateBandwidthHistory_allZeroForConstantPrices() {
        List<Double> prices = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            prices.add(100.0);
        }

        List<Double> history = service.calculateBandwidthHistory(prices);

        assertThat(history).allMatch(bw -> bw == 0.0);
    }

    // ========== BollingerBandAnalysis record tests ==========

    @Test
    void bollingerBandAnalysis_hasReliableDataWithMinimumPoints() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "SPY", "S&P 500", 100.0, 99.0, 101.0, 97.0, 0.5, 0.04, 50.0, List.of(), 40);

        assertThat(analysis.hasReliableData()).isTrue();
    }

    @Test
    void bollingerBandAnalysis_notReliableWithTooFewPoints() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "SPY", "S&P 500", 100.0, 99.0, 101.0, 97.0, 0.5, 0.04, 50.0, List.of(), 20);

        assertThat(analysis.hasReliableData()).isFalse();
    }

    @Test
    void bollingerBandAnalysis_formatsSummaryLineCorrectly() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "XLK",
                        "Technology",
                        150.0,
                        148.0,
                        152.0,
                        144.0,
                        0.75,
                        0.054,
                        65.0,
                        List.of(),
                        50);

        String summary = analysis.toSummaryLine();
        assertThat(summary).contains("XLK").contains("Technology").contains("%B");
    }

    @Test
    void bollingerBandAnalysis_formatsCompactLineCorrectly() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "XLE",
                        "Energy",
                        80.0,
                        79.0,
                        81.0,
                        77.0,
                        0.5,
                        0.05,
                        50.0,
                        List.of(BollingerSignalType.SQUEEZE),
                        50);

        String compact = analysis.toCompactLine();
        assertThat(compact).contains("XLE");
    }

    @Test
    void bollingerBandAnalysis_getInterpretationForNoSignals() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "SPY", "S&P 500", 100.0, 99.0, 101.0, 97.0, 0.5, 0.04, 50.0, List.of(), 50);

        assertThat(analysis.getInterpretation()).contains("normal");
    }

    @Test
    void bollingerBandAnalysis_getInterpretationForSqueeze() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "SPY",
                        "S&P 500",
                        100.0,
                        99.0,
                        101.0,
                        97.0,
                        0.5,
                        0.02,
                        5.0,
                        List.of(BollingerSignalType.SQUEEZE),
                        50);

        assertThat(analysis.getInterpretation()).contains("squeeze").contains("breakout");
    }

    @Test
    void bollingerBandAnalysis_isOverextended() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "XLK",
                        "Tech",
                        102.0,
                        100.0,
                        101.5,
                        98.5,
                        1.05,
                        0.03,
                        50.0,
                        List.of(BollingerSignalType.UPPER_BAND_TOUCH),
                        50);

        assertThat(analysis.isOverextended()).isTrue();
        assertThat(analysis.isUnderextended()).isFalse();
    }

    @Test
    void bollingerBandAnalysis_isUnderextended() {
        BollingerBandAnalysis analysis =
                new BollingerBandAnalysis(
                        "XLV",
                        "Healthcare",
                        97.0,
                        100.0,
                        101.5,
                        98.5,
                        -0.05,
                        0.03,
                        50.0,
                        List.of(BollingerSignalType.LOWER_BAND_TOUCH),
                        50);

        assertThat(analysis.isUnderextended()).isTrue();
        assertThat(analysis.isOverextended()).isFalse();
    }

    // ========== Helpers ==========

    private List<DailyPrice> generateDailyPrices(int count, double basePrice, double volatility) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate date = LocalDate.of(2025, 1, 1);
        for (int i = 0; i < count; i++) {
            double price = basePrice + volatility * Math.sin(i * 0.3);
            prices.add(new DailyPrice(date.plusDays(i), price));
        }
        return prices;
    }

    private List<Double> generatePriceList(int count, double basePrice, double volatility) {
        return IntStream.range(0, count)
                .mapToObj(i -> basePrice + volatility * Math.sin(i * 0.3))
                .toList();
    }
}
