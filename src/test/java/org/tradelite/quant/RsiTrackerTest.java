package org.tradelite.quant;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.anyLong;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.service.RsiService;
import org.tradelite.service.RsiService.RsiSignal;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class RsiTrackerTest {

    @Mock private RsiService rsiService;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;

    private RsiTracker tracker;

    private final StockSymbol apple = new StockSymbol("AAPL", "Apple");
    private final StockSymbol tesla = new StockSymbol("TSLA", "Tesla");

    @BeforeEach
    void setUp() {
        tracker = new RsiTracker(rsiService, telegramClient, symbolRegistry);
    }

    @Test
    void analyzeAndSendReport_sendsReportWithOverboughtSignal() {
        stubSymbols(apple);
        when(rsiService.analyze("AAPL", "Apple (AAPL)"))
                .thenReturn(
                        Optional.of(
                                new RsiSignal("Apple (AAPL)", 75.5, RsiSignal.Zone.OVERBOUGHT)));
        when(rsiService.getCurrentRsi(apple)).thenReturn(Optional.of(75.5));
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));

        tracker.analyzeAndSendReport();

        verify(telegramClient, times(1)).sendMessageAndReturnId(contains("RSI Signal Report"));
    }

    @Test
    void analyzeAndSendReport_noSignals_doesNotSend() {
        stubSymbols(apple);
        when(rsiService.analyze("AAPL", "Apple (AAPL)")).thenReturn(Optional.empty());

        tracker.analyzeAndSendReport();

        verify(telegramClient, never()).sendMessageAndReturnId(anyString());
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendReport_mixedSignals_includesBothSections() {
        stubSymbols(apple, tesla);
        when(rsiService.analyze("AAPL", "Apple (AAPL)"))
                .thenReturn(
                        Optional.of(
                                new RsiSignal("Apple (AAPL)", 75.5, RsiSignal.Zone.OVERBOUGHT)));
        when(rsiService.analyze("TSLA", "Tesla (TSLA)"))
                .thenReturn(
                        Optional.of(new RsiSignal("Tesla (TSLA)", 25.3, RsiSignal.Zone.OVERSOLD)));
        lenient().when(rsiService.getCurrentRsi(apple)).thenReturn(Optional.of(75.5));
        lenient().when(rsiService.getCurrentRsi(tesla)).thenReturn(Optional.of(25.3));
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));

        tracker.analyzeAndSendReport();

        verify(telegramClient, times(1))
                .sendMessageAndReturnId(
                        argThat(
                                report ->
                                        report.contains("Overbought")
                                                && report.contains("Oversold")
                                                && report.contains("2 signal(s)")));
    }

    @Test
    void analyzeAndSendReport_deletesPreviousReport() {
        stubSymbols(apple);
        when(rsiService.analyze("AAPL", "Apple (AAPL)"))
                .thenReturn(
                        Optional.of(
                                new RsiSignal("Apple (AAPL)", 75.5, RsiSignal.Zone.OVERBOUGHT)));
        lenient().when(rsiService.getCurrentRsi(apple)).thenReturn(Optional.of(75.5));
        when(telegramClient.sendMessageAndReturnId(anyString()))
                .thenReturn(OptionalLong.of(100L))
                .thenReturn(OptionalLong.of(200L));

        // First report
        tracker.analyzeAndSendReport();
        // Second report — should delete first
        tracker.analyzeAndSendReport();

        verify(telegramClient, times(1)).deleteMessage(100L);
    }

    @Test
    void analyzeAndSendReport_noDeleteWhenNoPreviousMessage() {
        stubSymbols(apple);
        when(rsiService.analyze("AAPL", "Apple (AAPL)"))
                .thenReturn(
                        Optional.of(
                                new RsiSignal("Apple (AAPL)", 75.5, RsiSignal.Zone.OVERBOUGHT)));
        lenient().when(rsiService.getCurrentRsi(apple)).thenReturn(Optional.of(75.5));
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(100L));

        tracker.analyzeAndSendReport();

        verify(telegramClient, never()).deleteMessage(anyLong());
    }

    @Test
    void analyzeAndSendReport_iteratesAllSymbolsFromRegistry() {
        StockSymbol msft = new StockSymbol("MSFT", "Microsoft");
        stubSymbols(apple, tesla, msft);
        when(rsiService.analyze(anyString(), anyString())).thenReturn(Optional.empty());

        tracker.analyzeAndSendReport();

        verify(rsiService).analyze("AAPL", "Apple (AAPL)");
        verify(rsiService).analyze("TSLA", "Tesla (TSLA)");
        verify(rsiService).analyze("MSFT", "Microsoft (MSFT)");
    }

    @Test
    void analyzeAndSendReport_deltaTracking_firstRunShowsZeroPreviousRsi() {
        stubSymbols(apple);
        when(rsiService.analyze("AAPL", "Apple (AAPL)"))
                .thenReturn(
                        Optional.of(
                                new RsiSignal("Apple (AAPL)", 75.5, RsiSignal.Zone.OVERBOUGHT)));
        when(rsiService.getCurrentRsi(apple)).thenReturn(Optional.of(75.5));
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));

        tracker.analyzeAndSendReport();

        // First run: previousRsi is 0, so no diff shown in report
        verify(telegramClient)
                .sendMessageAndReturnId(
                        argThat(report -> report.contains("75.50") && !report.contains("(+")));
    }

    @Test
    void analyzeAndSendReport_deltaTracking_subsequentRunShowsDiff() {
        stubSymbols(apple);
        when(rsiService.analyze("AAPL", "Apple (AAPL)"))
                .thenReturn(
                        Optional.of(
                                new RsiSignal("Apple (AAPL)", 75.5, RsiSignal.Zone.OVERBOUGHT)));
        when(rsiService.getCurrentRsi(apple)).thenReturn(Optional.of(75.5));
        when(telegramClient.sendMessageAndReturnId(anyString()))
                .thenReturn(OptionalLong.of(100L))
                .thenReturn(OptionalLong.of(200L));

        // First run — establishes previousRsi
        tracker.analyzeAndSendReport();

        // Second run — same RSI, so diff should be 0.0 and shown as (+0.0)
        tracker.analyzeAndSendReport();

        verify(telegramClient, times(2)).sendMessageAndReturnId(anyString());
    }

    @Test
    void reportFormat_overboughtSection() {
        stubSymbols(apple);
        when(rsiService.analyze("AAPL", "Apple (AAPL)"))
                .thenReturn(
                        Optional.of(
                                new RsiSignal("Apple (AAPL)", 80.0, RsiSignal.Zone.OVERBOUGHT)));
        lenient().when(rsiService.getCurrentRsi(apple)).thenReturn(Optional.of(80.0));
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));

        tracker.analyzeAndSendReport();

        verify(telegramClient)
                .sendMessageAndReturnId(
                        argThat(
                                report ->
                                        report.contains("*RSI Signal Report*")
                                                && report.contains("🔴 *Overbought (RSI ≥ 70):*")
                                                && report.contains("Apple (AAPL): 80.00")
                                                && report.contains(
                                                        "1 signal(s): 1 overbought, 0 oversold")));
    }

    @Test
    void reportFormat_oversoldSection() {
        stubSymbols(tesla);
        when(rsiService.analyze("TSLA", "Tesla (TSLA)"))
                .thenReturn(
                        Optional.of(new RsiSignal("Tesla (TSLA)", 20.0, RsiSignal.Zone.OVERSOLD)));
        lenient().when(rsiService.getCurrentRsi(tesla)).thenReturn(Optional.of(20.0));
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(123L));

        tracker.analyzeAndSendReport();

        verify(telegramClient)
                .sendMessageAndReturnId(
                        argThat(
                                report ->
                                        report.contains("🟢 *Oversold (RSI ≤ 30):*")
                                                && report.contains("Tesla (TSLA): 20.00")
                                                && report.contains(
                                                        "1 signal(s): 0 overbought, 1 oversold")));
    }

    // --- helpers ---

    private void stubSymbols(StockSymbol... symbols) {
        when(symbolRegistry.getAll()).thenReturn(List.of(symbols));
    }
}
