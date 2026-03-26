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
import org.tradelite.client.telegram.TelegramClient;

@ExtendWith(MockitoExtension.class)
class BollingerBandTrackerTest {

    @Mock private BollingerBandService bollingerBandService;
    @Mock private TelegramClient telegramClient;

    private BollingerBandTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new BollingerBandTracker(bollingerBandService, telegramClient);
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
    void trackAndAlert_doesNotSendMessageWhenNoData() {
        when(bollingerBandService.analyze(anyString(), anyString())).thenReturn(Optional.empty());

        tracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_doesNotSendMessageWhenNoSignals() {
        when(bollingerBandService.analyze(anyString(), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));

        tracker.trackAndAlert();

        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void trackAndAlert_sendsAlertWhenUpperBandTouch() {
        BollingerBandAnalysis upperTouch = upperBandAnalysis("XLK", "Technology");
        when(bollingerBandService.analyze(eq("XLK"), anyString()))
                .thenReturn(Optional.of(upperTouch));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("XLK")), anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        tracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertThat(message).contains("Bollinger Band Alert");
        assertThat(message).contains("Upper Band Touch");
        assertThat(message).contains("XLK");
    }

    @Test
    void trackAndAlert_sendsAlertWhenLowerBandTouch() {
        BollingerBandAnalysis lowerTouch = lowerBandAnalysis("XLE", "Energy");
        when(bollingerBandService.analyze(eq("XLE"), anyString()))
                .thenReturn(Optional.of(lowerTouch));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("XLE")), anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        tracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertThat(message).contains("Lower Band Touch");
        assertThat(message).contains("XLE");
    }

    @Test
    void trackAndAlert_sendsAlertWhenSqueezeDetected() {
        BollingerBandAnalysis squeeze = squeezeAnalysis("XLF", "Financials");
        when(bollingerBandService.analyze(eq("XLF"), anyString())).thenReturn(Optional.of(squeeze));
        when(bollingerBandService.analyze(argThat(s -> s != null && !s.equals("XLF")), anyString()))
                .thenReturn(Optional.of(normalAnalysis("OTHER", "Other")));

        tracker.trackAndAlert();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());
        String message = messageCaptor.getValue();
        assertThat(message).contains("Squeeze Detected");
        assertThat(message).contains("XLF");
    }

    @Test
    void sendDailyReport_sendsReportMessage() {
        when(bollingerBandService.analyze(anyString(), anyString()))
                .thenReturn(Optional.of(normalAnalysis("SPY", "S&P 500")));

        tracker.sendDailyReport();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());
        assertThat(messageCaptor.getValue()).contains("Bollinger Band Report");
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
}
