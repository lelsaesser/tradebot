package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.tradelite.service.DailyPriceProvider;
import org.tradelite.service.model.DailyPrice;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class EmaServiceTest {

    @Mock private DailyPriceProvider dailyPriceProvider;

    private EmaService service;

    @BeforeEach
    void setUp() {
        service = new EmaService(dailyPriceProvider);
    }

    @Test
    void analyze_returnsEmptyWhenInsufficientData() {
        List<DailyPrice> prices = generateDailyPrices(5, 150.0);
        when(dailyPriceProvider.findDailyClosingPrices("AAPL", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isEmpty();
    }

    @Test
    void analyze_returnsAnalysisWithFullData() {
        List<DailyPrice> prices = generateDailyPrices(250, 150.0);
        when(dailyPriceProvider.findDailyClosingPrices("AAPL", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.symbol()).isEqualTo("AAPL");
        assertThat(analysis.displayName()).isEqualTo("Apple");
        assertThat(analysis.currentPrice()).isGreaterThan(0);
        assertThat(analysis.ema9()).isGreaterThan(0);
        assertThat(analysis.ema21()).isGreaterThan(0);
        assertThat(analysis.ema50()).isGreaterThan(0);
        assertThat(analysis.ema100()).isGreaterThan(0);
        assertThat(analysis.ema200()).isGreaterThan(0);
        assertThat(analysis.emasAvailable()).isEqualTo(5);
    }

    @Test
    void analyze_partialEmas_53DataPoints() {
        List<DailyPrice> prices = generateDailyPrices(53, 150.0);
        when(dailyPriceProvider.findDailyClosingPrices("AAPL", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.ema9()).isGreaterThan(0);
        assertThat(analysis.ema21()).isGreaterThan(0);
        assertThat(analysis.ema50()).isGreaterThan(0);
        assertThat(analysis.ema100()).isNaN();
        assertThat(analysis.ema200()).isNaN();
        assertThat(analysis.emasAvailable()).isEqualTo(3);
    }

    @Test
    void analyze_partialEmas_25DataPoints() {
        List<DailyPrice> prices = generateDailyPrices(25, 150.0);
        when(dailyPriceProvider.findDailyClosingPrices("AAPL", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.ema9()).isGreaterThan(0);
        assertThat(analysis.ema21()).isGreaterThan(0);
        assertThat(analysis.ema50()).isNaN();
        assertThat(analysis.ema100()).isNaN();
        assertThat(analysis.ema200()).isNaN();
        assertThat(analysis.emasAvailable()).isEqualTo(2);
    }

    @Test
    void analyze_partialEmas_10DataPoints() {
        List<DailyPrice> prices = generateDailyPrices(10, 150.0);
        when(dailyPriceProvider.findDailyClosingPrices("AAPL", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.ema9()).isGreaterThan(0);
        assertThat(analysis.ema21()).isNaN();
        assertThat(analysis.ema50()).isNaN();
        assertThat(analysis.ema100()).isNaN();
        assertThat(analysis.ema200()).isNaN();
        assertThat(analysis.emasAvailable()).isEqualTo(1);
    }

    @Test
    void analyze_greenSignalWhenPriceAboveAllEmas() {
        List<DailyPrice> prices = generateRisingPrices(250, 100.0, 1.0);
        when(dailyPriceProvider.findDailyClosingPrices("MSFT", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("MSFT", "Microsoft");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.signalType()).isEqualTo(EmaSignalType.GREEN);
        assertThat(analysis.emasBelow()).isLessThanOrEqualTo(1);
        assertThat(analysis.emasAvailable()).isEqualTo(5);
    }

    @Test
    void analyze_redSignalWhenPriceBelowAllEmas() {
        List<DailyPrice> prices = generateFallingPrices(250, 300.0, 0.5);
        when(dailyPriceProvider.findDailyClosingPrices("TSLA", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("TSLA", "Tesla");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.signalType()).isEqualTo(EmaSignalType.RED);
        assertThat(analysis.emasBelow()).isEqualTo(5);
        assertThat(analysis.emasAvailable()).isEqualTo(5);
    }

    @Test
    void analyze_redSignalWithPartialEmas() {
        // Falling prices with only 25 data points — 2 EMAs available, below both = RED
        List<DailyPrice> prices = generateFallingPrices(25, 300.0, 5.0);
        when(dailyPriceProvider.findDailyClosingPrices("TSLA", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("TSLA", "Tesla");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.signalType()).isEqualTo(EmaSignalType.RED);
        assertThat(analysis.emasAvailable()).isEqualTo(2);
        assertThat(analysis.emasBelow()).isEqualTo(2);
    }

    @Test
    void countEmasBelow_priceAboveAll() {
        int count = EmaService.countEmasBelow(200.0, 100.0, 120.0, 150.0, 160.0, 190.0);
        assertThat(count).isZero();
    }

    @Test
    void countEmasBelow_priceBelowAll() {
        int count = EmaService.countEmasBelow(50.0, 100.0, 120.0, 150.0, 160.0, 190.0);
        assertThat(count).isEqualTo(5);
    }

    @Test
    void countEmasBelow_priceBelowSome() {
        int count = EmaService.countEmasBelow(130.0, 100.0, 120.0, 150.0, 160.0, 190.0);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void countEmasBelow_skipsNaN() {
        int count =
                EmaService.countEmasBelow(130.0, 100.0, 120.0, Double.NaN, Double.NaN, Double.NaN);
        assertThat(count).isZero();
    }

    @Test
    void computeEmaOrNaN_returnsNaNWhenInsufficientData() {
        List<Double> prices = List.of(1.0, 2.0, 3.0);
        assertThat(EmaService.computeEmaOrNaN(prices, 50)).isNaN();
    }

    @Test
    void computeEmaOrNaN_returnsValueWhenSufficientData() {
        List<Double> prices = IntStream.range(0, 50).mapToDouble(i -> 100.0).boxed().toList();
        assertThat(EmaService.computeEmaOrNaN(prices, 50)).isGreaterThan(0);
    }

    @Test
    void countAvailable_allPresent() {
        assertThat(EmaService.countAvailable(1.0, 2.0, 3.0, 4.0, 5.0)).isEqualTo(5);
    }

    @Test
    void countAvailable_someNaN() {
        assertThat(EmaService.countAvailable(1.0, 2.0, 3.0, Double.NaN, Double.NaN)).isEqualTo(3);
    }

    @Test
    void countAvailable_allNaN() {
        assertThat(
                        EmaService.countAvailable(
                                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN))
                .isZero();
    }

    // ========== Helper methods ==========

    private List<DailyPrice> generateDailyPrices(int count, double price) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(count);
        IntStream.range(0, count)
                .forEach(i -> prices.add(new DailyPrice(startDate.plusDays(i), price)));
        return prices;
    }

    private List<DailyPrice> generateRisingPrices(int count, double startPrice, double increment) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(count);
        IntStream.range(0, count)
                .forEach(
                        i ->
                                prices.add(
                                        new DailyPrice(
                                                startDate.plusDays(i),
                                                startPrice + (i * increment))));
        return prices;
    }

    private List<DailyPrice> generateFallingPrices(int count, double startPrice, double decrement) {
        List<DailyPrice> prices = new ArrayList<>();
        LocalDate startDate = LocalDate.now().minusDays(count);
        IntStream.range(0, count)
                .forEach(
                        i ->
                                prices.add(
                                        new DailyPrice(
                                                startDate.plusDays(i),
                                                Math.max(1.0, startPrice - (i * decrement)))));
        return prices;
    }
}
