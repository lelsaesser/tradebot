package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.SymbolRegistry;

@ExtendWith(MockitoExtension.class)
class TailRiskTrackerTest {

    private static final TailRiskWindow SHORT = TailRiskTracker.SHORT;
    private static final TailRiskWindow LONG = TailRiskTracker.LONG;

    @Mock private TailRiskService tailRiskService;

    @Mock private TelegramGateway telegramClient;

    @Mock private SymbolRegistry symbolRegistry;

    private TailRiskTracker tailRiskTracker;

    @BeforeEach
    void setUp() {
        tailRiskTracker = new TailRiskTracker(tailRiskService, telegramClient, symbolRegistry);
        Map<String, String> etfs = new LinkedHashMap<>();
        etfs.put(SymbolRegistry.BENCHMARK_SYMBOL, SymbolRegistry.BENCHMARK_NAME);
        etfs.putAll(SymbolRegistry.BROAD_SECTOR_ETFS);
        etfs.putAll(SymbolRegistry.THEMATIC_ETFS);
        lenient().when(symbolRegistry.getAllEtfs()).thenReturn(etfs);
    }

    @Test
    void analyzeAllSectors_returnsListOfAnalyses() {
        TailRiskAnalysis spyAnalysis =
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.1);
        TailRiskAnalysis xleAnalysis =
                createAnalysis("XLE", "Energy", 4.2, TailRiskLevel.MODERATE, -0.3);

        // Default: return empty for all symbols
        lenient()
                .when(
                        tailRiskService.analyzeTailRisk(
                                anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        // Override specific symbols on the short window
        when(tailRiskService.analyzeTailRisk("SPY", "S&P 500", SHORT))
                .thenReturn(Optional.of(spyAnalysis));
        when(tailRiskService.analyzeTailRisk("XLE", "Energy", SHORT))
                .thenReturn(Optional.of(xleAnalysis));

        List<TailRiskAnalysis> results = tailRiskTracker.analyzeAllSectors(SHORT);

        assertThat(results).hasSize(2).contains(spyAnalysis, xleAnalysis);
    }

    @Test
    void analyzeAllSectors_callsServiceForBothWindows() {
        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());

        tailRiskTracker.analyzeAllSectors(SHORT);
        tailRiskTracker.analyzeAllSectors(LONG);

        // For at least the benchmark symbol, both windows must have been queried.
        verify(tailRiskService)
                .analyzeTailRisk(
                        SymbolRegistry.BENCHMARK_SYMBOL, SymbolRegistry.BENCHMARK_NAME, SHORT);
        verify(tailRiskService)
                .analyzeTailRisk(
                        SymbolRegistry.BENCHMARK_SYMBOL, SymbolRegistry.BENCHMARK_NAME, LONG);
    }

    @Test
    void trackAndAlert_sendsNoAlertWhenNeitherWindowHasElevatedRisk() {
        TailRiskAnalysis lowRiskAnalysis =
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.0);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.of(lowRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_sendsOnlyShortWindowAlertWhenOnlyShortHasElevatedRisk() {
        TailRiskAnalysis lowRiskAnalysis =
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.0);
        TailRiskAnalysis highRiskShort =
                createAnalysis("XLE", "Energy", 7.0, TailRiskLevel.HIGH, -0.5);

        // Default LOW for everything in both windows.
        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.of(lowRiskAnalysis));
        // Short window has elevated risk for XLE; long window stays LOW.
        when(tailRiskService.analyzeTailRisk("XLE", "Energy", SHORT))
                .thenReturn(Optional.of(highRiskShort));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("[35d]").contains("XLE");
    }

    @Test
    void trackAndAlert_sendsOnlyLongWindowAlertWhenOnlyLongHasElevatedRisk() {
        TailRiskAnalysis lowRiskAnalysis =
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.0);
        TailRiskAnalysis highRiskLong =
                createAnalysis("XLE", "Energy", 7.0, TailRiskLevel.HIGH, -0.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.of(lowRiskAnalysis));
        when(tailRiskService.analyzeTailRisk("XLE", "Energy", LONG))
                .thenReturn(Optional.of(highRiskLong));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(1)).sendMessage(captor.capture());
        assertThat(captor.getValue()).contains("[252d]").contains("XLE");
    }

    @Test
    void trackAndAlert_sendsBothAlertsInShortThenLongOrderWhenBothHaveElevatedRisk() {
        TailRiskAnalysis lowRiskAnalysis =
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.0);
        TailRiskAnalysis highRiskShort =
                createAnalysis("XLE", "Energy", 7.0, TailRiskLevel.HIGH, -0.5);
        TailRiskAnalysis highRiskLong =
                createAnalysis("XLF", "Financials", 8.0, TailRiskLevel.HIGH, -0.6);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.of(lowRiskAnalysis));
        when(tailRiskService.analyzeTailRisk("XLE", "Energy", SHORT))
                .thenReturn(Optional.of(highRiskShort));
        when(tailRiskService.analyzeTailRisk("XLF", "Financials", LONG))
                .thenReturn(Optional.of(highRiskLong));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient, times(2)).sendMessage(captor.capture());
        List<String> messages = captor.getAllValues();
        // Stable order: 35d first, 252d second.
        assertThat(messages.get(0)).contains("[35d]").contains("XLE");
        assertThat(messages.get(1)).contains("[252d]").contains("XLF");

        InOrder inOrder = inOrder(telegramClient);
        inOrder.verify(telegramClient).sendMessage(messages.get(0));
        inOrder.verify(telegramClient).sendMessage(messages.get(1));
    }

    @Test
    void trackAndAlert_sendsAlertWithExtremeRiskHeader() {
        TailRiskAnalysis extremeRiskAnalysis =
                createAnalysis("XLF", "Financials", 10.0, TailRiskLevel.EXTREME, -1.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLF", "Financials", SHORT))
                .thenReturn(Optional.of(extremeRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage).contains("Extreme").contains("🔴").contains("[35d]");
    }

    @Test
    void trackAndAlert_noAlertWhenNoData() {
        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());

        tailRiskTracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_includesSkewnessInterpretation() {
        // High risk with negative skewness (crash bias)
        TailRiskAnalysis highRiskAnalysis =
                createAnalysis("XLK", "Technology", 7.5, TailRiskLevel.HIGH, -1.2);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLK", "Technology", SHORT))
                .thenReturn(Optional.of(highRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage).contains("Skewness").contains("direction");
    }

    @Test
    void trackAndAlert_includesDirectionalBiasForCrashRisk() {
        TailRiskAnalysis crashRiskAnalysis =
                createAnalysis("XLE", "Energy", 8.0, TailRiskLevel.HIGH, -1.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLE", "Energy", SHORT))
                .thenReturn(Optional.of(crashRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage).contains("crash");
    }

    @Test
    void trackAndAlert_includesDirectionalBiasForRallyPotential() {
        TailRiskAnalysis rallyPotentialAnalysis =
                createAnalysis("XLK", "Technology", 8.0, TailRiskLevel.HIGH, 1.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLK", "Technology", SHORT))
                .thenReturn(Optional.of(rallyPotentialAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage).contains("rally");
    }

    @Test
    void buildSummaryReport_includesBothWindowSections() {
        TailRiskAnalysis spyShort = createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.1);
        TailRiskAnalysis xleShort = createAnalysis("XLE", "Energy", 6.5, TailRiskLevel.HIGH, -0.8);
        TailRiskAnalysis spyLong = createAnalysis("SPY", "S&P 500", 3.2, TailRiskLevel.LOW, 0.05);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("SPY", "S&P 500", SHORT))
                .thenReturn(Optional.of(spyShort));
        when(tailRiskService.analyzeTailRisk("XLE", "Energy", SHORT))
                .thenReturn(Optional.of(xleShort));
        when(tailRiskService.analyzeTailRisk("SPY", "S&P 500", LONG))
                .thenReturn(Optional.of(spyLong));

        tailRiskTracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());
        String report = captor.getValue();

        assertThat(report)
                .contains("Tail Risk Report")
                .contains("[35d window]")
                .contains("[252d window]")
                .contains("SPY")
                .contains("XLE");
        // Short section appears before long section.
        assertThat(report.indexOf("[35d window]")).isLessThan(report.indexOf("[252d window]"));
    }

    @Test
    void sendDailyReport_showsElevatedCountWhenRiskPresent() {
        TailRiskAnalysis highRiskAnalysis =
                createAnalysis("XLF", "Financials", 7.0, TailRiskLevel.HIGH, -0.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLF", "Financials", SHORT))
                .thenReturn(Optional.of(highRiskAnalysis));

        tailRiskTracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());

        assertThat(captor.getValue()).contains("elevated tail risk");
    }

    @Test
    void sendDailyReport_showsAllNormalWhenNoRisk() {
        TailRiskAnalysis lowRiskAnalysis =
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.0);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.of(lowRiskAnalysis));

        tailRiskTracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());

        assertThat(captor.getValue()).contains("All sectors within normal");
    }

    @Test
    void sendDailyReport_handlesInsufficientData() {
        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());

        tailRiskTracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());

        assertThat(captor.getValue()).contains("Insufficient data");
    }

    @Test
    void sendDailyReport_sendsSummaryReportViaTelegram() {
        TailRiskAnalysis analysis = createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.1);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("SPY", "S&P 500", SHORT))
                .thenReturn(Optional.of(analysis));

        tailRiskTracker.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String report = messageCaptor.getValue();
        assertThat(report).contains("Tail Risk Report").contains("SPY");
    }

    @Test
    void sendDailyReport_includesSkewnessInformation() {
        TailRiskAnalysis analysis = createAnalysis("XLE", "Energy", 6.5, TailRiskLevel.HIGH, -1.2);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString(), any(TailRiskWindow.class)))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLE", "Energy", SHORT))
                .thenReturn(Optional.of(analysis));

        tailRiskTracker.sendDailyReport();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(captor.capture());

        // Report should include skewness info
        assertThat(captor.getValue()).contains("Skew");
    }

    /** Helper method to create TailRiskAnalysis with skewness. */
    private TailRiskAnalysis createAnalysis(
            String symbol,
            String displayName,
            double kurtosis,
            TailRiskLevel riskLevel,
            double skewness) {
        double excessKurtosis = kurtosis - 3.0;
        SkewnessLevel skewnessLevel = SkewnessLevel.fromSkewness(skewness);
        return new TailRiskAnalysis(
                symbol,
                displayName,
                kurtosis,
                excessKurtosis,
                riskLevel,
                skewness,
                skewnessLevel,
                25);
    }
}
