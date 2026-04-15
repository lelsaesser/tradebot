package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;

@SuppressWarnings("SameParameterValue")
@ExtendWith(MockitoExtension.class)
class BollingerBandTrackerTest {

    @Mock private BollingerBandService bollingerBandService;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;

    private BollingerBandTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new BollingerBandTracker(bollingerBandService, telegramClient, symbolRegistry);
        // Default: no tracked stocks (tests that need stocks will override)
        lenient().when(symbolRegistry.getStocks()).thenReturn(List.of());
        // Default: return all ETFs via getAllEtfs()
        lenient().when(symbolRegistry.getAllEtfs()).thenReturn(defaultEtfMap());
    }

    @Test
    void analyzeAllSectors_returnsResultsForAvailableData() {
        when(bollingerBandService.analyze(eq("SPY"), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));
        when(bollingerBandService.analyze(eq("XLK"), anyString()))
                .thenReturn(Optional.of(normalAnalysis("XLK", "Technology")));
        // All other sectors return empty
        when(bollingerBandService.analyze(
                        argThat(s -> s != null && !s.equals("SPY") && !s.equals("XLK")),
                        anyString()))
                .thenReturn(Optional.empty());

        List<BollingerBandAnalysis> results = tracker.analyzeAllSectors();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(BollingerBandAnalysis::symbol).contains("SPY", "XLK");
    }

    @Test
    void analyzeAllStocks_returnsResultsForTrackedStocks() {
        when(symbolRegistry.getStocks())
                .thenReturn(
                        List.of(
                                new StockSymbol("AAPL", "Apple Inc"),
                                new StockSymbol("MSFT", "Microsoft Corp")));

        when(bollingerBandService.analyze("AAPL", "Apple Inc"))
                .thenReturn(Optional.of(normalAnalysis("AAPL", "Apple Inc")));
        when(bollingerBandService.analyze("MSFT", "Microsoft Corp"))
                .thenReturn(Optional.of(normalAnalysis("MSFT", "Microsoft Corp")));

        List<BollingerBandAnalysis> results = tracker.analyzeAllStocks();

        assertThat(results).hasSize(2);
        assertThat(results).extracting(BollingerBandAnalysis::symbol).contains("AAPL", "MSFT");
    }

    @Test
    void analyzeAllStocks_excludesEtfSymbols() {
        // getStocks() already filters out ETFs, so only AAPL should appear
        when(symbolRegistry.getStocks()).thenReturn(List.of(new StockSymbol("AAPL", "Apple Inc")));

        when(bollingerBandService.analyze("AAPL", "Apple Inc"))
                .thenReturn(Optional.of(normalAnalysis("AAPL", "Apple Inc")));

        List<BollingerBandAnalysis> results = tracker.analyzeAllStocks();

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().symbol()).isEqualTo("AAPL");
        verify(bollingerBandService, never()).analyze("SPY", "S&P 500 ETF");
    }

    @Test
    void analyzeAllStocks_returnsEmptyWhenNoTrackedStocks() {
        when(symbolRegistry.getStocks()).thenReturn(List.of());

        List<BollingerBandAnalysis> results = tracker.analyzeAllStocks();

        assertThat(results).isEmpty();
    }

    @Test
    void analyzeAndSendAlerts_doesNotSendMessageWhenNoData() {
        when(bollingerBandService.analyze(anyString(), anyString())).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_doesNotSendMessageWhenNoSignals() {
        when(bollingerBandService.analyze(anyString(), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndAlert_sendsSendAlertsWhenUpperBandTouch() {
        BollingerBandAnalysis upperTouch = upperBandAnalysis("XLK", "Technology");
        when(bollingerBandService.analyze(eq("XLK"), anyString()))
                .thenReturn(Optional.of(upperTouch));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("XLK")), anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessageAndReturnId(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertThat(message).contains("Bollinger Band Alert");
        assertThat(message).contains("Upper Band Touch");
        assertThat(message).contains("XLK");
    }

    @Test
    void analyzeAndAlert_sendsSendAlertsWhenLowerBandTouch() {
        BollingerBandAnalysis lowerTouch = lowerBandAnalysis("XLE", "Energy");
        when(bollingerBandService.analyze(eq("XLE"), anyString()))
                .thenReturn(Optional.of(lowerTouch));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("XLE")), anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessageAndReturnId(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertThat(message).contains("Lower Band Touch");
        assertThat(message).contains("XLE");
    }

    @Test
    void analyzeAndAlert_sendsSendAlertsWhenSqueezeDetected() {
        BollingerBandAnalysis squeeze = squeezeAnalysis("XLF", "Financials");
        when(bollingerBandService.analyze(eq("XLF"), anyString())).thenReturn(Optional.of(squeeze));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("XLF")), anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessageAndReturnId(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertThat(message).contains("Squeeze Detected");
        assertThat(message).contains("XLF");
    }

    @Test
    void analyzeAndAlert_includesStockSignalsInSendAlerts() {
        // Sector returns normal
        when(bollingerBandService.analyze(anyString(), anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        // Stock with signal
        when(symbolRegistry.getStocks()).thenReturn(List.of(new StockSymbol("TSLA", "Tesla Inc")));
        when(bollingerBandService.analyze("TSLA", "Tesla Inc"))
                .thenReturn(Optional.of(upperBandAnalysis("TSLA", "Tesla Inc")));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessageAndReturnId(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertThat(message).contains("TSLA");
        assertThat(message).contains("Upper Band Touch");
    }

    @Test
    void sendDailyReport_sendsReportMessage() {
        when(bollingerBandService.analyze(anyString(), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));

        tracker.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Bollinger Band Report");
        verify(telegramClient, never()).sendMessageAndReturnId(anyString());
        verify(telegramClient, never()).deleteMessage(anyLong());
    }

    @Test
    void sendDailyReport_doesNotDeletePreviousOnSecondCall() {
        when(bollingerBandService.analyze(anyString(), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));

        // First call
        tracker.sendDailyReport();

        // Second call — should NOT delete anything (daily report doesn't track message IDs)
        tracker.sendDailyReport();
        verify(telegramClient, never()).deleteMessage(anyLong());
    }

    @Test
    void analyzeAndSendAlerts_deletesPreviousHourlyAlertOnSecondCall() {
        BollingerBandAnalysis squeezeAnalysis = squeezeAnalysis("XLK", "Technology");
        when(bollingerBandService.analyze(eq("XLK"), anyString()))
                .thenReturn(Optional.of(squeezeAnalysis));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("XLK")), anyString()))
                .thenReturn(Optional.empty());
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(42L));

        // First call — sends alert, no previous to delete
        tracker.analyzeAndSendAlerts();
        verify(telegramClient, never()).deleteMessage(anyLong());

        // Second call — should delete message 42 before sending new alert
        when(telegramClient.sendMessageAndReturnId(anyString())).thenReturn(OptionalLong.of(99L));
        tracker.analyzeAndSendAlerts();
        verify(telegramClient, times(1)).deleteMessage(42L);
    }

    @Test
    void buildSummaryReport_showsNoSignalsMessage() {
        when(bollingerBandService.analyze(anyString(), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));

        String report = tracker.buildSummaryReport();

        assertThat(report).contains("normal Bollinger Band range");
    }

    @Test
    void buildSummaryReport_showsInsufficientDataWhenNoResults() {
        when(bollingerBandService.analyze(anyString(), anyString())).thenReturn(Optional.empty());

        String report = tracker.buildSummaryReport();

        assertThat(report).contains("Insufficient data");
    }

    @Test
    void buildSummaryReport_showsSignalCountWhenSignalsPresent() {
        when(bollingerBandService.analyze(eq("XLK"), anyString()))
                .thenReturn(Optional.of(upperBandAnalysis("XLK", "Technology")));
        when(bollingerBandService.analyze(eq("XLE"), anyString()))
                .thenReturn(Optional.of(squeezeAnalysis("XLE", "Energy")));
        when(bollingerBandService.analyze(
                        argThat(s -> s != null && !s.equals("XLK") && !s.equals("XLE")),
                        anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        String report = tracker.buildSummaryReport();

        assertThat(report).contains("active signals");
    }

    @Test
    void buildSummaryReport_showsSectorAndStockSections() {
        // Sector data
        when(bollingerBandService.analyze(eq("SPY"), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));
        when(bollingerBandService.analyze(
                        argThat(s -> s != null && !s.equals("SPY") && !s.equals("AAPL")),
                        anyString()))
                .thenReturn(Optional.empty());

        // Stock data
        when(symbolRegistry.getStocks()).thenReturn(List.of(new StockSymbol("AAPL", "Apple Inc")));
        when(bollingerBandService.analyze("AAPL", "Apple Inc"))
                .thenReturn(Optional.of(normalAnalysis("AAPL", "Apple Inc")));

        String report = tracker.buildSummaryReport();

        assertThat(report).contains("Sector ETFs:");
        assertThat(report).contains("Stocks:");
        assertThat(report).contains("SPY");
        assertThat(report).contains("AAPL");
    }

    @Test
    void buildSummaryReport_omitsStockSectionWhenNoStocks() {
        when(bollingerBandService.analyze(eq("SPY"), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("SPY")), anyString()))
                .thenReturn(Optional.empty());

        String report = tracker.buildSummaryReport();

        assertThat(report).contains("Sector ETFs:");
        assertThat(report).doesNotContain("Stocks:");
    }

    // ========== Helper methods ==========

    private BollingerBandAnalysis normalAnalysis(String symbol, String displayName) {
        return new BollingerBandAnalysis(
                symbol, displayName, 100.0, 99.5, 101.5, 97.5, 0.5, 0.04, 50.0, List.of(), 50);
    }

    private BollingerBandAnalysis upperBandAnalysis(String symbol, String displayName) {
        return new BollingerBandAnalysis(
                symbol,
                displayName,
                102.0,
                100.0,
                101.5,
                98.5,
                1.05,
                0.03,
                50.0,
                List.of(BollingerSignalType.UPPER_BAND_TOUCH),
                50);
    }

    private BollingerBandAnalysis lowerBandAnalysis(String symbol, String displayName) {
        return new BollingerBandAnalysis(
                symbol,
                displayName,
                97.0,
                100.0,
                101.5,
                98.5,
                -0.05,
                0.03,
                50.0,
                List.of(BollingerSignalType.LOWER_BAND_TOUCH),
                50);
    }

    private BollingerBandAnalysis squeezeAnalysis(String symbol, String displayName) {
        return new BollingerBandAnalysis(
                symbol,
                displayName,
                100.0,
                100.0,
                100.5,
                99.5,
                0.5,
                0.01,
                5.0,
                List.of(BollingerSignalType.SQUEEZE),
                50);
    }

    private static Map<String, String> defaultEtfMap() {
        Map<String, String> etfs = new LinkedHashMap<>();
        etfs.put(SymbolRegistry.BENCHMARK_SYMBOL, SymbolRegistry.BENCHMARK_NAME);
        etfs.putAll(SymbolRegistry.BROAD_SECTOR_ETFS);
        etfs.putAll(SymbolRegistry.THEMATIC_ETFS);
        return etfs;
    }
}
