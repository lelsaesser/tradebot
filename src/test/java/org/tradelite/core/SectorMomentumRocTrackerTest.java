package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.core.MomentumRocSignal.SignalType;
import org.tradelite.service.MomentumRocService;

@ExtendWith(MockitoExtension.class)
class SectorMomentumRocTrackerTest {

    @Mock private MomentumRocService momentumRocService;
    @Mock private TelegramClient telegramClient;

    private SectorMomentumRocTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SectorMomentumRocTracker(momentumRocService, telegramClient);
    }

    @Test
    void analyzeAndSendAlerts_noSignals_noMessage() {
        when(momentumRocService.detectMomentumShift(any(), any())).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void analyzeAndSendAlerts_positiveSignal_sendsAlert() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        2.5,
                        1.8,
                        -1.0,
                        -0.5);
        when(momentumRocService.detectMomentumShift("XLK", "Technology"))
                .thenReturn(Optional.of(signal));
        when(momentumRocService.detectMomentumShift(
                        argThat(s -> !s.equals("XLK")), argThat(s -> !s.equals("Technology"))))
                .thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("SECTOR MOMENTUM ROC ALERT"));
        assertTrue(message.contains("MOMENTUM TURNING POSITIVE"));
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("XLK"));
    }

    @Test
    void analyzeAndSendAlerts_negativeSignal_sendsAlert() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLE",
                        "Energy",
                        SignalType.MOMENTUM_TURNING_NEGATIVE,
                        -1.2,
                        -0.5,
                        0.5,
                        0.2);
        when(momentumRocService.detectMomentumShift("XLE", "Energy"))
                .thenReturn(Optional.of(signal));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("MOMENTUM TURNING NEGATIVE"));
        assertTrue(message.contains("Energy"));
    }

    @Test
    void analyzeAndSendAlerts_mixedSignals_sendsGroupedAlert() {
        MomentumRocSignal positiveSignal =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        2.5,
                        1.8,
                        -1.0,
                        -0.5);
        MomentumRocSignal negativeSignal =
                new MomentumRocSignal(
                        "XLE",
                        "Energy",
                        SignalType.MOMENTUM_TURNING_NEGATIVE,
                        -1.2,
                        -0.5,
                        0.5,
                        0.2);

        when(momentumRocService.detectMomentumShift("XLK", "Technology"))
                .thenReturn(Optional.of(positiveSignal));
        when(momentumRocService.detectMomentumShift("XLE", "Energy"))
                .thenReturn(Optional.of(negativeSignal));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("MOMENTUM TURNING POSITIVE"));
        assertTrue(message.contains("MOMENTUM TURNING NEGATIVE"));
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("Energy"));
    }

    @Test
    void analyzeAndSendAlerts_callsServiceForAllSectorEtfs() {
        when(momentumRocService.detectMomentumShift(any(), any())).thenReturn(Optional.empty());

        tracker.analyzeAndSendAlerts();

        // Verify all 11 SPDR sector ETFs are checked
        verify(momentumRocService).detectMomentumShift("XLK", "Technology");
        verify(momentumRocService).detectMomentumShift("XLF", "Financials");
        verify(momentumRocService).detectMomentumShift("XLE", "Energy");
        verify(momentumRocService).detectMomentumShift("XLV", "Health Care");
        verify(momentumRocService).detectMomentumShift("XLY", "Cons. Discretionary");
        verify(momentumRocService).detectMomentumShift("XLP", "Cons. Staples");
        verify(momentumRocService).detectMomentumShift("XLI", "Industrials");
        verify(momentumRocService).detectMomentumShift("XLC", "Communication");
        verify(momentumRocService).detectMomentumShift("XLRE", "Real Estate");
        verify(momentumRocService).detectMomentumShift("XLB", "Materials");
        verify(momentumRocService).detectMomentumShift("XLU", "Utilities");
    }

    @Test
    void analyzeAndSendAlerts_messageContainsRocValues() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        3.5,
                        2.1,
                        -2.0,
                        -1.5);
        when(momentumRocService.detectMomentumShift("XLK", "Technology"))
                .thenReturn(Optional.of(signal));

        tracker.analyzeAndSendAlerts();

        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        // Should contain ROC values formatted
        assertTrue(message.contains("+3.5") || message.contains("3.5"));
    }

    @Test
    void analyzeAndSendAlerts_serviceException_continuesProcessing() {
        // XLK throws exception
        when(momentumRocService.detectMomentumShift("XLK", "Technology"))
                .thenThrow(new RuntimeException("API Error"));
        // Other sectors return empty
        when(momentumRocService.detectMomentumShift(
                        argThat(s -> !s.equals("XLK")), argThat(s -> !s.equals("Technology"))))
                .thenReturn(Optional.empty());

        // Should not throw
        assertDoesNotThrow(() -> tracker.analyzeAndSendAlerts());

        // Should still check other sectors
        verify(momentumRocService, atLeast(10)).detectMomentumShift(any(), any());
    }

    @Test
    void formatAlertMessage_positiveSignals_groupedCorrectly() {
        MomentumRocSignal signal1 =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        2.0,
                        1.5,
                        -1.0,
                        -0.5);
        MomentumRocSignal signal2 =
                new MomentumRocSignal(
                        "XLF",
                        "Financials",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        1.5,
                        1.0,
                        -0.5,
                        -0.3);

        String message = tracker.formatAlertMessage(java.util.List.of(signal1, signal2));

        assertTrue(message.contains("MOMENTUM TURNING POSITIVE"));
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("Financials"));
        assertFalse(message.contains("MOMENTUM TURNING NEGATIVE"));
    }

    @Test
    void formatAlertMessage_negativeSignals_groupedCorrectly() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLE",
                        "Energy",
                        SignalType.MOMENTUM_TURNING_NEGATIVE,
                        -2.0,
                        -1.5,
                        1.0,
                        0.5);

        String message = tracker.formatAlertMessage(java.util.List.of(signal));

        assertTrue(message.contains("MOMENTUM TURNING NEGATIVE"));
        assertTrue(message.contains("Energy"));
        assertFalse(message.contains("MOMENTUM TURNING POSITIVE:"));
    }

    @Test
    void formatAlertMessage_mixedSignals_bothSectionsIncluded() {
        MomentumRocSignal positiveSignal =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        2.0,
                        1.5,
                        -1.0,
                        -0.5);
        MomentumRocSignal negativeSignal =
                new MomentumRocSignal(
                        "XLE",
                        "Energy",
                        SignalType.MOMENTUM_TURNING_NEGATIVE,
                        -2.0,
                        -1.5,
                        1.0,
                        0.5);

        String message =
                tracker.formatAlertMessage(java.util.List.of(positiveSignal, negativeSignal));

        assertTrue(message.contains("MOMENTUM TURNING POSITIVE"));
        assertTrue(message.contains("MOMENTUM TURNING NEGATIVE"));
        assertTrue(message.contains("ROC"));
    }
}
