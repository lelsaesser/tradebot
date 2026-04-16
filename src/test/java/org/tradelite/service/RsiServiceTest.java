package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.model.DailyPrice;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class RsiServiceTest {

    @Mock private DailyPriceProvider dailyPriceProvider;

    private RsiService rsiService;

    private final StockSymbol appleSymbol = new StockSymbol("AAPL", "Apple");

    @BeforeEach
    void setUp() {
        rsiService = new RsiService(dailyPriceProvider);
    }

    // --- calculateRsi tests ---

    @Test
    void calculateRsi_allGains_returns100() {
        List<Double> prices =
                List.of(
                        100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 107.0, 108.0, 109.0, 110.0,
                        111.0, 112.0, 113.0, 114.0);

        double rsi = rsiService.calculateRsi(prices);

        assertEquals(100.0, rsi, 0.001);
    }

    @Test
    void calculateRsi_allLosses_returnsNearZero() {
        List<Double> prices =
                List.of(
                        100.0, 95.0, 90.0, 85.0, 80.0, 75.0, 70.0, 65.0, 60.0, 55.0, 50.0, 45.0,
                        40.0, 35.0, 30.0);

        double rsi = rsiService.calculateRsi(prices);

        assertThat(rsi, is(closeTo(0.0, 0.1)));
    }

    @Test
    void calculateRsi_mixedPrices_returnsBetween0And100() {
        List<Double> prices =
                List.of(
                        100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0, 108.0, 114.0, 110.0,
                        116.0, 112.0, 118.0, 114.0);

        double rsi = rsiService.calculateRsi(prices);

        assertThat(rsi, is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void calculateRsi_insufficientData_returns50() {
        List<Double> prices = List.of(100.0, 105.0, 102.0, 108.0, 104.0, 110.0);

        double rsi = rsiService.calculateRsi(prices);

        assertEquals(50, rsi);
    }

    // --- getCurrentRsi tests ---

    @Test
    void getCurrentRsi_withSufficientData_returnsRsi() {
        stubDailyPrices("AAPL", risingPrices(15));

        Optional<Double> rsi = rsiService.getCurrentRsi(appleSymbol);

        assertThat(rsi.isPresent(), is(true));
        assertThat(rsi.get(), is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void getCurrentRsi_withInsufficientData_returnsEmpty() {
        stubDailyPrices("AAPL", risingPrices(10));

        Optional<Double> rsi = rsiService.getCurrentRsi(appleSymbol);

        assertThat(rsi.isEmpty(), is(true));
    }

    @Test
    void getCurrentRsi_noData_returnsEmpty() {
        stubDailyPrices("AAPL", List.of());

        Optional<Double> rsi = rsiService.getCurrentRsi(appleSymbol);

        assertThat(rsi.isEmpty(), is(true));
    }

    // --- analyze tests ---

    @Test
    void analyze_overbought_returnsSignal() {
        stubDailyPrices("AAPL", risingPrices(15));

        Optional<RsiService.RsiSignal> signal = rsiService.analyze("AAPL", "Apple (AAPL)");

        assertThat(signal.isPresent(), is(true));
        assertThat(signal.get().zone(), is(RsiService.RsiSignal.Zone.OVERBOUGHT));
        assertThat(signal.get().displayName(), is("Apple (AAPL)"));
        assertThat(signal.get().rsi(), is(greaterThanOrEqualTo(70.0)));
    }

    @Test
    void analyze_oversold_returnsSignal() {
        stubDailyPrices("TSLA", decliningPrices(15));

        Optional<RsiService.RsiSignal> signal = rsiService.analyze("TSLA", "Tesla (TSLA)");

        assertThat(signal.isPresent(), is(true));
        assertThat(signal.get().zone(), is(RsiService.RsiSignal.Zone.OVERSOLD));
        assertThat(signal.get().rsi(), is(lessThanOrEqualTo(30.0)));
    }

    @Test
    void analyze_neutral_returnsEmpty() {
        List<DailyPrice> mixedPrices =
                toDailyPrices(
                        100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0, 108.0, 114.0, 110.0,
                        116.0, 112.0, 118.0, 114.0);
        when(dailyPriceProvider.findDailyClosingPrices("AAPL", RsiService.RSI_LOOKBACK_DAYS))
                .thenReturn(mixedPrices);

        Optional<RsiService.RsiSignal> signal = rsiService.analyze("AAPL", "Apple (AAPL)");

        assertThat(signal.isEmpty(), is(true));
    }

    @Test
    void analyze_insufficientData_returnsEmpty() {
        stubDailyPrices("AAPL", risingPrices(10));

        Optional<RsiService.RsiSignal> signal = rsiService.analyze("AAPL", "Apple (AAPL)");

        assertThat(signal.isEmpty(), is(true));
    }

    // --- helpers ---

    private void stubDailyPrices(String symbol, List<DailyPrice> prices) {
        when(dailyPriceProvider.findDailyClosingPrices(symbol, RsiService.RSI_LOOKBACK_DAYS))
                .thenReturn(prices);
    }

    private List<DailyPrice> risingPrices(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prices.add(new DailyPrice(LocalDate.now().minusDays(count - 1 - i), 100.0 + i));
        }
        return prices;
    }

    private List<DailyPrice> decliningPrices(int count) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            prices.add(new DailyPrice(LocalDate.now().minusDays(count - 1 - i), 200.0 - (i * 5)));
        }
        return prices;
    }

    private List<DailyPrice> toDailyPrices(double... values) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            prices.add(new DailyPrice(LocalDate.now().minusDays(values.length - 1 - i), values[i]));
        }
        return prices;
    }
}
