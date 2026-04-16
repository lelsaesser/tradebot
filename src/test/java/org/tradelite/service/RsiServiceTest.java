package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.service.model.DailyPrice;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class RsiServiceTest {

    @Mock private TelegramGateway telegramClient;
    @Mock private DailyPriceProvider dailyPriceProvider;
    @Mock private SymbolRegistry symbolRegistry;

    private RsiService rsiService;

    private final StockSymbol appleSymbol = new StockSymbol("AAPL", "Apple");

    @BeforeEach
    void setUp() {
        rsiService = new RsiService(telegramClient, dailyPriceProvider, symbolRegistry);
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

    // --- analyzeAllSymbols tests ---

    @Test
    void analyzeAllSymbols_detectsOverbought() {
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", risingPrices(15));

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(
                signals.stream().anyMatch(s -> s.zone() == RsiService.RsiSignal.Zone.OVERBOUGHT),
                is(true));
        assertThat(
                signals.stream().anyMatch(s -> "Apple (AAPL)".equals(s.displayName())), is(true));
    }

    @Test
    void analyzeAllSymbols_detectsOversold() {
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", decliningPrices(15));

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(
                signals.stream().anyMatch(s -> s.zone() == RsiService.RsiSignal.Zone.OVERSOLD),
                is(true));
    }

    @Test
    void analyzeAllSymbols_insufficientData_noSignals() {
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", risingPrices(10));

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeAllSymbols_neutralRsi_noSignals() {
        stubSymbolRegistry(appleSymbol);
        List<DailyPrice> mixedPrices =
                toDailyPrices(
                        100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0, 108.0, 114.0, 110.0,
                        116.0, 112.0, 118.0, 114.0);
        when(dailyPriceProvider.findDailyClosingPrices("AAPL", RsiService.RSI_LOOKBACK_DAYS))
                .thenReturn(mixedPrices);

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(empty()));
    }

    @Test
    void analyzeAllSymbols_usesDisplayNameFromStockSymbol() {
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", risingPrices(15));

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(signals.getFirst().displayName(), is("Apple (AAPL)"));
    }

    @Test
    void analyzeAllSymbols_previousRsiDelta_firstRunShowsZero() {
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", risingPrices(15));

        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        assertThat(signals.getFirst().previousRsi(), is(0.0));
    }

    @Test
    void analyzeAllSymbols_previousRsiDelta_subsequentRunShowsDiff() {
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", risingPrices(15));

        // First run — establishes previousRsi
        rsiService.analyzeAllSymbols();
        // Second run — should show diff from first run
        List<RsiService.RsiSignal> signals = rsiService.analyzeAllSymbols();

        assertThat(signals, is(not(empty())));
        // previousRsi should now be non-zero (set from the first analysis)
        assertThat(signals.getFirst().previousRsi(), is(not(0.0)));
        // rsiDiff should be 0 since same prices produce same RSI
        assertThat(signals.getFirst().rsiDiff(), is(closeTo(0.0, 0.01)));
    }

    // --- sendRsiReport tests ---

    @Test
    void sendRsiReport_sendsConsolidatedReport() {
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", risingPrices(15));

        rsiService.sendRsiReport();

        verify(telegramClient, times(1)).sendMessageAndReturnId(contains("RSI Signal Report"));
    }

    @Test
    void sendRsiReport_noSignals_doesNotSend() {
        when(symbolRegistry.getAll()).thenReturn(List.of());

        rsiService.sendRsiReport();

        verify(telegramClient, never()).sendMessageAndReturnId(anyString());
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void sendRsiReport_deletesPreviousReport() {
        when(telegramClient.sendMessageAndReturnId(anyString()))
                .thenReturn(OptionalLong.of(100L))
                .thenReturn(OptionalLong.of(200L));
        StockSymbol msft = new StockSymbol("MSFT", "Microsoft");
        stubSymbolRegistry(appleSymbol, msft);
        stubDailyPrices("AAPL", risingPrices(15));
        stubDailyPrices("MSFT", risingPrices(15));

        // First report
        rsiService.sendRsiReport();
        // Second report — should delete first
        rsiService.sendRsiReport();

        verify(telegramClient, times(1)).deleteMessage(100L);
    }

    @Test
    void sendRsiReport_noDeleteWhenNoPreviousMessage() {
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(100L));
        stubSymbolRegistry(appleSymbol);
        stubDailyPrices("AAPL", risingPrices(15));

        rsiService.sendRsiReport();

        verify(telegramClient, never()).deleteMessage(anyLong());
    }

    @Test
    void sendRsiReport_analyzesAndSendsInOneStep() {
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));
        StockSymbol tsla = new StockSymbol("TSLA", "Tesla");
        stubSymbolRegistry(appleSymbol, tsla);
        stubDailyPrices("AAPL", risingPrices(15));
        stubDailyPrices("TSLA", decliningPrices(15));

        rsiService.sendRsiReport();

        verify(telegramClient, times(1))
                .sendMessageAndReturnId(
                        argThat(
                                report ->
                                        report.contains("RSI Signal Report")
                                                && report.contains("Overbought")
                                                && report.contains("Oversold")));
    }

    // --- buildRsiReport tests ---

    @Test
    void buildRsiReport_overboughtSignals() {
        List<RsiService.RsiSignal> signals =
                List.of(
                        new RsiService.RsiSignal(
                                "Apple", 75.5, 72.0, 3.5, RsiService.RsiSignal.Zone.OVERBOUGHT),
                        new RsiService.RsiSignal(
                                "Microsoft",
                                80.2,
                                78.0,
                                2.2,
                                RsiService.RsiSignal.Zone.OVERBOUGHT));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("*RSI Signal Report*"));
        assertThat(report, containsString("Overbought (RSI"));
        assertThat(report, containsString("Apple"));
        assertThat(report, containsString("Microsoft"));
        assertThat(report, containsString("2 signal(s): 2 overbought, 0 oversold"));
    }

    @Test
    void buildRsiReport_oversoldSignals() {
        List<RsiService.RsiSignal> signals =
                List.of(
                        new RsiService.RsiSignal(
                                "Tesla", 25.3, 28.0, -2.7, RsiService.RsiSignal.Zone.OVERSOLD));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("Oversold (RSI"));
        assertThat(report, containsString("Tesla"));
        assertThat(report, containsString("1 signal(s): 0 overbought, 1 oversold"));
    }

    @Test
    void buildRsiReport_mixedSignals() {
        List<RsiService.RsiSignal> signals =
                List.of(
                        new RsiService.RsiSignal(
                                "Apple", 75.5, 72.0, 3.5, RsiService.RsiSignal.Zone.OVERBOUGHT),
                        new RsiService.RsiSignal(
                                "Tesla", 25.3, 28.0, -2.7, RsiService.RsiSignal.Zone.OVERSOLD));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("Overbought"));
        assertThat(report, containsString("Oversold"));
        assertThat(report, containsString("2 signal(s): 1 overbought, 1 oversold"));
    }

    @Test
    void buildRsiReport_withNoPreviousRsi_omitsDiff() {
        List<RsiService.RsiSignal> signals =
                List.of(
                        new RsiService.RsiSignal(
                                "Apple", 75.5, 0, 75.5, RsiService.RsiSignal.Zone.OVERBOUGHT));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("Apple: 75.50"));
        assertThat(report, not(containsString("(+")));
    }

    @Test
    void buildRsiReport_withRsiDiff() {
        List<RsiService.RsiSignal> signals =
                List.of(
                        new RsiService.RsiSignal(
                                "Apple", 75.5, 72.0, 3.5, RsiService.RsiSignal.Zone.OVERBOUGHT));

        String report = rsiService.buildRsiReport(signals);

        assertThat(report, containsString("Apple: 75.50 (+3.5)"));
    }

    // --- helpers ---

    private void stubSymbolRegistry(StockSymbol... symbols) {
        when(symbolRegistry.getAll()).thenReturn(List.of(symbols));
    }

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
