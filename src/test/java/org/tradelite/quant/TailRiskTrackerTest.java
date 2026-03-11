package org.tradelite.quant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramClient;

@ExtendWith(MockitoExtension.class)
class TailRiskTrackerTest {

    @Mock private TailRiskService tailRiskService;

    @Mock private TelegramClient telegramClient;

    private TailRiskTracker tailRiskTracker;

    @BeforeEach
    void setUp() {
        tailRiskTracker = new TailRiskTracker(tailRiskService, telegramClient);
    }

    @Test
    void analyzeAllSectors_returnsListOfAnalyses() {
        TailRiskAnalysis spyAnalysis =
                new TailRiskAnalysis("SPY", "S&P 500", 3.5, 0.5, TailRiskLevel.LOW, 25);
        TailRiskAnalysis xleAnalysis =
                new TailRiskAnalysis("XLE", "Energy", 4.2, 1.2, TailRiskLevel.MODERATE, 25);

        // Default: return empty for all symbols
        lenient()
                .when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        // Override specific symbols
        when(tailRiskService.analyzeTailRisk("SPY", "S&P 500"))
                .thenReturn(Optional.of(spyAnalysis));
        when(tailRiskService.analyzeTailRisk("XLE", "Energy")).thenReturn(Optional.of(xleAnalysis));

        List<TailRiskAnalysis> results = tailRiskTracker.analyzeAllSectors();

        assertThat(results).hasSize(2).contains(spyAnalysis, xleAnalysis);
    }

    @Test
    void trackAndAlert_sendsNoAlertWhenAllSectorsLowRisk() {
        TailRiskAnalysis lowRiskAnalysis =
                new TailRiskAnalysis("SPY", "S&P 500", 3.5, 0.5, TailRiskLevel.LOW, 25);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.of(lowRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_sendsAlertWhenHighRiskDetected() {
        TailRiskAnalysis highRiskAnalysis =
                new TailRiskAnalysis("XLE", "Energy", 7.0, 4.0, TailRiskLevel.HIGH, 25);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLE", "Energy"))
                .thenReturn(Optional.of(highRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage)
                .contains("TAIL RISK ALERT")
                .contains("XLE")
                .contains("Energy")
                .contains("HIGH");
    }

    @Test
    void trackAndAlert_sendsAlertWithExtremeRiskHeader() {
        TailRiskAnalysis extremeRiskAnalysis =
                new TailRiskAnalysis("XLF", "Financials", 10.0, 7.0, TailRiskLevel.EXTREME, 25);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLF", "Financials"))
                .thenReturn(Optional.of(extremeRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage).contains("EXTREME").contains("🔴");
    }

    @Test
    void trackAndAlert_noAlertWhenNoData() {
        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());

        tailRiskTracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_includesInterpretationGuidance() {
        TailRiskAnalysis highRiskAnalysis =
                new TailRiskAnalysis("XLK", "Technology", 7.5, 4.5, TailRiskLevel.HIGH, 25);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLK", "Technology"))
                .thenReturn(Optional.of(highRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage).contains("Big moves more likely").contains("macro conditions");
    }

    @Test
    void buildSummaryReport_includesAllSectorAnalyses() {
        TailRiskAnalysis spyAnalysis =
                new TailRiskAnalysis("SPY", "S&P 500", 3.5, 0.5, TailRiskLevel.LOW, 25);
        TailRiskAnalysis xleAnalysis =
                new TailRiskAnalysis("XLE", "Energy", 6.5, 3.5, TailRiskLevel.HIGH, 25);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("SPY", "S&P 500"))
                .thenReturn(Optional.of(spyAnalysis));
        when(tailRiskService.analyzeTailRisk("XLE", "Energy")).thenReturn(Optional.of(xleAnalysis));

        String report = tailRiskTracker.buildSummaryReport();

        assertThat(report).contains("Tail Risk Report").contains("SPY").contains("XLE");
    }

    @Test
    void buildSummaryReport_showsElevatedCountWhenRiskPresent() {
        TailRiskAnalysis highRiskAnalysis =
                new TailRiskAnalysis("XLF", "Financials", 7.0, 4.0, TailRiskLevel.HIGH, 25);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLF", "Financials"))
                .thenReturn(Optional.of(highRiskAnalysis));

        String report = tailRiskTracker.buildSummaryReport();

        assertThat(report).contains("elevated tail risk");
    }

    @Test
    void buildSummaryReport_showsAllNormalWhenNoRisk() {
        TailRiskAnalysis lowRiskAnalysis =
                new TailRiskAnalysis("SPY", "S&P 500", 3.5, 0.5, TailRiskLevel.LOW, 25);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.of(lowRiskAnalysis));

        String report = tailRiskTracker.buildSummaryReport();

        assertThat(report).contains("All sectors within normal");
    }

    @Test
    void buildSummaryReport_handlesInsufficientData() {
        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());

        String report = tailRiskTracker.buildSummaryReport();

        assertThat(report).contains("Insufficient data");
    }
}
