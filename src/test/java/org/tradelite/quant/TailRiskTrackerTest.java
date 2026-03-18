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
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.1);
        TailRiskAnalysis xleAnalysis =
                createAnalysis("XLE", "Energy", 4.2, TailRiskLevel.MODERATE, -0.3);

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
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.0);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.of(lowRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_sendsAlertWhenHighRiskDetected() {
        TailRiskAnalysis highRiskAnalysis =
                createAnalysis("XLE", "Energy", 7.0, TailRiskLevel.HIGH, -0.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLE", "Energy"))
                .thenReturn(Optional.of(highRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage)
                .contains("Tail Risk Alert")
                .contains("XLE")
                .contains("Energy")
                .contains("High");
    }

    @Test
    void trackAndAlert_sendsAlertWithExtremeRiskHeader() {
        TailRiskAnalysis extremeRiskAnalysis =
                createAnalysis("XLF", "Financials", 10.0, TailRiskLevel.EXTREME, -1.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLF", "Financials"))
                .thenReturn(Optional.of(extremeRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        assertThat(alertMessage).contains("Extreme").contains("🔴");
    }

    @Test
    void trackAndAlert_noAlertWhenNoData() {
        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());

        tailRiskTracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_includesSkewnessInterpretation() {
        // High risk with negative skewness (crash bias)
        TailRiskAnalysis highRiskAnalysis =
                createAnalysis("XLK", "Technology", 7.5, TailRiskLevel.HIGH, -1.2);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLK", "Technology"))
                .thenReturn(Optional.of(highRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        // Should include skewness-related content
        assertThat(alertMessage).contains("Skewness").contains("direction");
    }

    @Test
    void trackAndAlert_includesDirectionalBiasForCrashRisk() {
        // High risk with negative skewness (crash bias)
        TailRiskAnalysis crashRiskAnalysis =
                createAnalysis("XLE", "Energy", 8.0, TailRiskLevel.HIGH, -1.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLE", "Energy"))
                .thenReturn(Optional.of(crashRiskAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        // Should include crash risk indication
        assertThat(alertMessage).contains("crash");
    }

    @Test
    void trackAndAlert_includesDirectionalBiasForRallyPotential() {
        // High risk with positive skewness (rally potential)
        TailRiskAnalysis rallyPotentialAnalysis =
                createAnalysis("XLK", "Technology", 8.0, TailRiskLevel.HIGH, 1.5);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLK", "Technology"))
                .thenReturn(Optional.of(rallyPotentialAnalysis));

        tailRiskTracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String alertMessage = messageCaptor.getValue();
        // Should include rally potential indication
        assertThat(alertMessage).contains("rally");
    }

    @Test
    void buildSummaryReport_includesAllSectorAnalyses() {
        TailRiskAnalysis spyAnalysis =
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.1);
        TailRiskAnalysis xleAnalysis =
                createAnalysis("XLE", "Energy", 6.5, TailRiskLevel.HIGH, -0.8);

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
                createAnalysis("XLF", "Financials", 7.0, TailRiskLevel.HIGH, -0.5);

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
                createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.0);

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

    @Test
    void sendDailyReport_sendsSummaryReportViaTelegram() {
        TailRiskAnalysis analysis = createAnalysis("SPY", "S&P 500", 3.5, TailRiskLevel.LOW, 0.1);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("SPY", "S&P 500")).thenReturn(Optional.of(analysis));

        tailRiskTracker.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String report = messageCaptor.getValue();
        assertThat(report).contains("Tail Risk Report").contains("SPY");
    }

    @Test
    void buildSummaryReport_includesSkewnessInformation() {
        TailRiskAnalysis analysis = createAnalysis("XLE", "Energy", 6.5, TailRiskLevel.HIGH, -1.2);

        when(tailRiskService.analyzeTailRisk(anyString(), anyString()))
                .thenReturn(Optional.empty());
        when(tailRiskService.analyzeTailRisk("XLE", "Energy")).thenReturn(Optional.of(analysis));

        String report = tailRiskTracker.buildSummaryReport();

        // Report should include skewness info
        assertThat(report).contains("Skew");
    }

    /**
     * Helper method to create TailRiskAnalysis with skewness.
     *
     * @param symbol The stock symbol
     * @param displayName The display name
     * @param kurtosis The kurtosis value
     * @param riskLevel The risk level
     * @param skewness The skewness value
     * @return A TailRiskAnalysis instance
     */
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
