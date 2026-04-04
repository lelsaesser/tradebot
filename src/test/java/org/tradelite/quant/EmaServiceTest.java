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
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

@ExtendWith(MockitoExtension.class)
class EmaServiceTest {

    @Mock private PriceQuoteRepository priceQuoteRepository;

    private EmaService service;

    @BeforeEach
    void setUp() {
        service = new EmaService(priceQuoteRepository);
    }

    @Test
    void analyze_returnsEmptyWhenInsufficientData() {
        List<DailyPrice> prices = generateDailyPrices(100, 150.0);
        when(priceQuoteRepository.findDailyClosingPrices("AAPL", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("AAPL", "Apple");

        assertThat(result).isEmpty();
    }

    @Test
    void analyze_returnsAnalysisWithSufficientData() {
        List<DailyPrice> prices = generateDailyPrices(250, 150.0);
        when(priceQuoteRepository.findDailyClosingPrices("AAPL", EmaService.LOOKBACK_CALENDAR_DAYS))
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
    }

    @Test
    void analyze_greenSignalWhenPriceAboveAllEmas() {
        // Rising prices: current price will be above all EMAs
        List<DailyPrice> prices = generateRisingPrices(250, 100.0, 1.0);
        when(priceQuoteRepository.findDailyClosingPrices("MSFT", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("MSFT", "Microsoft");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.signalType()).isEqualTo(EmaSignalType.GREEN);
        assertThat(analysis.emasBelow()).isLessThanOrEqualTo(1);
    }

    @Test
    void analyze_redSignalWhenPriceBelowAllEmas() {
        // Falling prices: current price will be below all EMAs
        List<DailyPrice> prices = generateFallingPrices(250, 300.0, 0.5);
        when(priceQuoteRepository.findDailyClosingPrices("TSLA", EmaService.LOOKBACK_CALENDAR_DAYS))
                .thenReturn(prices);

        Optional<EmaAnalysis> result = service.analyze("TSLA", "Tesla");

        assertThat(result).isPresent();
        EmaAnalysis analysis = result.get();
        assertThat(analysis.signalType()).isEqualTo(EmaSignalType.RED);
        assertThat(analysis.emasBelow()).isEqualTo(5);
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
