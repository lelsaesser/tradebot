package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

@ExtendWith(MockitoExtension.class)
class VfiTrackerTest {

    @Mock private VfiService vfiService;
    @Mock private RelativeStrengthService relativeStrengthService;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;
    @Mock private FeatureToggleService featureToggleService;

    private VfiTracker tracker;

    @BeforeEach
    void setUp() {
        tracker =
                new VfiTracker(
                        vfiService,
                        relativeStrengthService,
                        telegramClient,
                        symbolRegistry,
                        featureToggleService);
        lenient().when(featureToggleService.isEnabled(FeatureToggle.VFI_REPORT)).thenReturn(true);
        lenient().when(symbolRegistry.getAll()).thenReturn(List.of());
    }

    @Test
    void sendDailyReport_featureToggleDisabled_skipsReport() {
        when(featureToggleService.isEnabled(FeatureToggle.VFI_REPORT)).thenReturn(false);

        tracker.sendDailyReport();

        verify(telegramClient, never()).sendMessage(anyString());
        verify(vfiService, never()).analyze(anyString(), anyString());
    }

    @Test
    void sendDailyReport_sendsReportWithAllThreeSignalColors() {
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("XLK", "Technology"),
                                new StockSymbol("XLF", "Financials"),
                                new StockSymbol("XLU", "Utilities")));

        // XLK: GREEN (RS positive + VFI positive)
        mockVfi("XLK", "Technology", 3.4, 2.1);
        mockRs("XLK", 1.05, 1.04); // rs > ema → positive

        // XLF: YELLOW (RS positive + VFI negative)
        mockVfi("XLF", "Financials", 0.8, -0.3);
        mockRs("XLF", 1.02, 1.01); // rs > ema → positive, but VFI signal < 0

        // XLU: RED (RS negative + VFI negative)
        mockVfi("XLU", "Utilities", -4.2, -3.1);
        mockRs("XLU", 0.95, 0.98); // rs < ema → negative

        tracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());

        String report = captor.getValue();
        assertThat(report).contains("*RS + VFI Combined Report*");
        assertThat(report).contains("*Both Positive (RS↑ + VFI↑):*");
        assertThat(report).contains("*Mixed Signal:*");
        assertThat(report).contains("*Both Negative (RS↓ + VFI↓):*");
        assertThat(report).contains("XLK (Technology)");
        assertThat(report).contains("XLF (Financials)");
        assertThat(report).contains("XLU (Utilities)");
    }

    @Test
    void sendDailyReport_emptyData_noTelegramSend() {
        // allEtfs() returns real ETFs but none have VFI/RS data
        // Default: symbolRegistry.getAll() returns empty list (from setUp)
        // VfiService/RS not stubbed → return default null which Mockito makes Optional.empty()
        lenient().when(vfiService.analyze(anyString(), anyString())).thenReturn(Optional.empty());
        lenient()
                .when(relativeStrengthService.getCurrentRsResult(anyString()))
                .thenReturn(Optional.empty());

        tracker.sendDailyReport();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void sendDailyReport_skipsSymbolsWithInsufficientVfiData() {
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("XLK", "Technology"),
                                new StockSymbol("XLF", "Financials")));

        // XLK has VFI, XLF does not
        mockVfi("XLK", "Technology", 3.4, 2.1);
        mockRs("XLK", 1.05, 1.04);
        lenient().when(vfiService.analyze(eq("XLF"), anyString())).thenReturn(Optional.empty());
        mockRs("XLF", 1.02, 1.01);

        tracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String report = captor.getValue();
        assertThat(report).contains("XLK");
        assertThat(report).doesNotContain("XLF");
    }

    @Test
    void sendDailyReport_skipsSymbolsWithInsufficientRsData() {
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("XLK", "Technology"),
                                new StockSymbol("XLF", "Financials")));

        mockVfi("XLK", "Technology", 3.4, 2.1);
        mockRs("XLK", 1.05, 1.04);
        mockVfi("XLF", "Financials", 0.8, 0.5);
        lenient()
                .when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.empty());

        tracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String report = captor.getValue();
        assertThat(report).contains("XLK");
        assertThat(report).doesNotContain("XLF");
    }

    @Test
    void sendDailyReport_includesStocksExcludingEtfs() {
        StockSymbol aapl = new StockSymbol("AAPL", "Apple Inc");
        StockSymbol xlk = new StockSymbol("XLK", "Technology");
        when(symbolRegistry.getAll()).thenReturn(List.of(aapl, xlk));

        // AAPL gets VFI + RS data
        mockVfi("AAPL", "Apple Inc", 2.0, 1.5);
        mockRs("AAPL", 1.03, 1.01);

        // XLK has no VFI data
        lenient().when(vfiService.analyze(eq("XLK"), anyString())).thenReturn(Optional.empty());

        tracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String report = captor.getValue();
        assertThat(report).contains("AAPL (Apple Inc)");
    }

    @Test
    void sendDailyReport_summaryFooterShowsCorrectCounts() {
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("XLK", "Technology"),
                                new StockSymbol("XLF", "Financials"),
                                new StockSymbol("XLU", "Utilities")));

        // 2 GREEN, 1 YELLOW, 0 RED
        mockVfi("XLK", "Technology", 3.4, 2.1);
        mockRs("XLK", 1.05, 1.04);
        mockVfi("XLF", "Financials", 1.0, 0.5);
        mockRs("XLF", 1.02, 1.01);
        mockVfi("XLU", "Utilities", 0.8, -0.3);
        mockRs("XLU", 1.01, 0.99);

        tracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String report = captor.getValue();
        assertThat(report).contains("🟢 2 | 🟡 1 | 🔴 0");
    }

    @Test
    void sendDailyReport_rsPercentageCalculation() {
        when(symbolRegistry.getAll()).thenReturn(List.of(new StockSymbol("XLK", "Technology")));

        // rs=1.05, ema=1.04 → (1.05/1.04 - 1) * 100 = 0.96...% → +1.0%
        mockVfi("XLK", "Technology", 3.4, 2.1);
        mockRs("XLK", 1.05, 1.04);

        tracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String report = captor.getValue();
        assertThat(report).contains("RS +1.0%");
    }

    @Test
    void sendDailyReport_singleColorReport_onlyRendersNonEmptySections() {
        when(symbolRegistry.getAll())
                .thenReturn(
                        List.of(
                                new StockSymbol("XLK", "Technology"),
                                new StockSymbol("XLF", "Financials")));

        // All GREEN — no YELLOW or RED sections
        mockVfi("XLK", "Technology", 3.4, 2.1);
        mockRs("XLK", 1.05, 1.04);
        mockVfi("XLF", "Financials", 1.0, 0.5);
        mockRs("XLF", 1.02, 1.01);

        tracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String report = captor.getValue();
        assertThat(report).contains("*Both Positive (RS↑ + VFI↑):*");
        assertThat(report).doesNotContain("*Mixed Signal:*");
        assertThat(report).doesNotContain("*Both Negative (RS↓ + VFI↓):*");
        // Footer still shows all three counts
        assertThat(report).contains("🟢 2 | 🟡 0 | 🔴 0");
    }

    private void mockVfi(String symbol, String displayName, double vfi, double signal) {
        lenient()
                .when(vfiService.analyze(eq(symbol), anyString()))
                .thenReturn(Optional.of(new VfiAnalysis(symbol, displayName, vfi, signal)));
    }

    private void mockRs(String symbol, double rs, double ema) {
        lenient()
                .when(relativeStrengthService.getCurrentRsResult(symbol))
                .thenReturn(Optional.of(new RsResult(rs, ema, 50, true)));
    }
}
