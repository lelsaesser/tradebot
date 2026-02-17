package org.tradelite.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.StockSymbolRegistry;

class RelativeStrengthTrackerTest {

    private RelativeStrengthService relativeStrengthService;
    private TargetPriceProvider targetPriceProvider;
    private StockSymbolRegistry stockSymbolRegistry;
    private TelegramClient telegramClient;

    private RelativeStrengthTracker tracker;

    @BeforeEach
    void setUp() {
        relativeStrengthService = mock(RelativeStrengthService.class);
        targetPriceProvider = mock(TargetPriceProvider.class);
        stockSymbolRegistry = mock(StockSymbolRegistry.class);
        telegramClient = mock(TelegramClient.class);

        tracker =
                new RelativeStrengthTracker(
                        relativeStrengthService,
                        targetPriceProvider,
                        stockSymbolRegistry,
                        telegramClient);
    }

    @Test
    void testAnalyzeAndSendAlerts_noSignals() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("AAPL", 150.0, 200.0)));
        when(stockSymbolRegistry.fromString("AAPL"))
                .thenReturn(Optional.of(new StockSymbol("AAPL", "Apple")));
        when(relativeStrengthService.calculateRelativeStrength(eq("AAPL"), anyString()))
                .thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        // No signals = no message sent
        verify(telegramClient, never()).sendMessage(any());
        verify(relativeStrengthService).saveRsHistory();
    }

    @Test
    void testAnalyzeAndSendAlerts_withOutperformingSignal() throws IOException {
        TargetPrice targetPrice = new TargetPrice("NVDA", 100.0, 150.0);
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of(targetPrice));
        when(stockSymbolRegistry.fromString("NVDA"))
                .thenReturn(Optional.of(new StockSymbol("NVDA", "Nvidia")));

        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "NVDA",
                        "Nvidia",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.25,
                        1.18,
                        5.9);
        when(relativeStrengthService.calculateRelativeStrength(eq("NVDA"), anyString()))
                .thenReturn(Optional.of(signal));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("RELATIVE STRENGTH ALERT"));
        assertThat(message, containsString("OUTPERFORMING SPY"));
        assertThat(message, containsString("NVDA"));
        assertThat(message, containsString("Nvidia"));
        assertThat(message, containsString("1.25"));
        assertThat(message, containsString("1.18"));
    }

    @Test
    void testAnalyzeAndSendAlerts_withUnderperformingSignal() throws IOException {
        TargetPrice targetPrice = new TargetPrice("INTC", 30.0, 50.0);
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of(targetPrice));
        when(stockSymbolRegistry.fromString("INTC"))
                .thenReturn(Optional.of(new StockSymbol("INTC", "Intel")));

        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "INTC",
                        "Intel",
                        RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                        0.85,
                        0.92,
                        -7.6);
        when(relativeStrengthService.calculateRelativeStrength(eq("INTC"), anyString()))
                .thenReturn(Optional.of(signal));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("UNDERPERFORMING SPY"));
        assertThat(message, containsString("INTC"));
        assertThat(message, containsString("Intel"));
    }

    @Test
    void testAnalyzeAndSendAlerts_multipleSignals() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(
                        List.of(
                                new TargetPrice("NVDA", 100.0, 150.0),
                                new TargetPrice("INTC", 30.0, 50.0)));
        when(stockSymbolRegistry.fromString("NVDA"))
                .thenReturn(Optional.of(new StockSymbol("NVDA", "Nvidia")));
        when(stockSymbolRegistry.fromString("INTC"))
                .thenReturn(Optional.of(new StockSymbol("INTC", "Intel")));

        RelativeStrengthSignal outperforming =
                new RelativeStrengthSignal(
                        "NVDA",
                        "Nvidia",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.25,
                        1.18,
                        5.9);
        RelativeStrengthSignal underperforming =
                new RelativeStrengthSignal(
                        "INTC",
                        "Intel",
                        RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                        0.85,
                        0.92,
                        -7.6);

        when(relativeStrengthService.calculateRelativeStrength(eq("NVDA"), anyString()))
                .thenReturn(Optional.of(outperforming));
        when(relativeStrengthService.calculateRelativeStrength(eq("INTC"), anyString()))
                .thenReturn(Optional.of(underperforming));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertThat(message, containsString("OUTPERFORMING SPY"));
        assertThat(message, containsString("UNDERPERFORMING SPY"));
        assertThat(message, containsString("NVDA"));
        assertThat(message, containsString("INTC"));
    }

    @Test
    void testAnalyzeAndSendAlerts_skipsBenchmark() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("SPY", 400.0, 500.0)));

        tracker.analyzeAndSendAlerts();

        // SPY should be skipped
        verify(relativeStrengthService, never()).calculateRelativeStrength(eq("SPY"), anyString());
        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void testAnalyzeAndSendAlerts_usesSymbolAsDisplayNameWhenNotFound() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("UNKNOWN", 100.0, 150.0)));
        when(stockSymbolRegistry.fromString("UNKNOWN")).thenReturn(Optional.empty());
        when(relativeStrengthService.calculateRelativeStrength("UNKNOWN", "UNKNOWN"))
                .thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        // Should use "UNKNOWN" as display name when stock symbol not found in registry
        verify(relativeStrengthService).calculateRelativeStrength("UNKNOWN", "UNKNOWN");
    }

    @Test
    void testAnalyzeAndSendAlerts_handlesExceptionGracefully() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(
                        List.of(
                                new TargetPrice("AAPL", 150.0, 200.0),
                                new TargetPrice("NVDA", 100.0, 150.0)));
        when(stockSymbolRegistry.fromString("AAPL"))
                .thenReturn(Optional.of(new StockSymbol("AAPL", "Apple")));
        when(stockSymbolRegistry.fromString("NVDA"))
                .thenReturn(Optional.of(new StockSymbol("NVDA", "Nvidia")));

        // First stock throws exception
        when(relativeStrengthService.calculateRelativeStrength(eq("AAPL"), anyString()))
                .thenThrow(new RuntimeException("Test exception"));
        // Second stock returns signal
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "NVDA",
                        "Nvidia",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.25,
                        1.18,
                        5.9);
        when(relativeStrengthService.calculateRelativeStrength(eq("NVDA"), anyString()))
                .thenReturn(Optional.of(signal));

        tracker.analyzeAndSendAlerts();

        // Should still send message for NVDA
        verify(telegramClient).sendMessage(contains("NVDA"));
    }

    @Test
    void testFormatAlertMessage_emptyLists() {
        String message = tracker.formatAlertMessage(List.of(), List.of());

        assertThat(message, containsString("RELATIVE STRENGTH ALERT"));
        assertThat(message, containsString("50-period EMA crossover"));
        assertThat(message, not(containsString("OUTPERFORMING SPY")));
        assertThat(message, not(containsString("UNDERPERFORMING SPY")));
    }

    @Test
    void testFormatAlertMessage_onlyOutperforming() {
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "NVDA",
                        "Nvidia",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.25,
                        1.18,
                        5.9);

        String message = tracker.formatAlertMessage(List.of(signal), List.of());

        assertThat(message, containsString("OUTPERFORMING SPY"));
        assertThat(message, containsString("NVDA"));
        assertThat(message, not(containsString("UNDERPERFORMING SPY")));
    }

    @Test
    void testFormatAlertMessage_onlyUnderperforming() {
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "INTC",
                        "Intel",
                        RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                        0.85,
                        0.92,
                        -7.6);

        String message = tracker.formatAlertMessage(List.of(), List.of(signal));

        assertThat(message, not(containsString("OUTPERFORMING SPY")));
        assertThat(message, containsString("UNDERPERFORMING SPY"));
        assertThat(message, containsString("INTC"));
    }
}
