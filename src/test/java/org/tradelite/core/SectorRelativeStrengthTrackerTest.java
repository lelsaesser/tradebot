package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.*;
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
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

@ExtendWith(MockitoExtension.class)
class SectorRelativeStrengthTrackerTest {

    @Mock private RelativeStrengthService relativeStrengthService;

    @Mock private TelegramClient telegramClient;

    private SectorRelativeStrengthTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SectorRelativeStrengthTracker(relativeStrengthService, telegramClient);
    }

    @Test
    void sendDailySectorRsSummary_withMixedPerformance_sendsFormattedMessage() {
        // Given: Some sectors outperforming, some underperforming
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.02, 50, true))); // +2.94%
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.01, 1.00, 50, true))); // +1.0%
        when(relativeStrengthService.getCurrentRsResult("XLU"))
                .thenReturn(Optional.of(new RsResult(0.95, 1.00, 50, true))); // -5.0%
        when(relativeStrengthService.getCurrentRsResult("XLRE"))
                .thenReturn(Optional.of(new RsResult(0.98, 1.00, 50, true))); // -2.0%

        // Return empty for other sectors
        when(relativeStrengthService.getCurrentRsResult("XLE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLV")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLY")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLP")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLI")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLC")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLB")).thenReturn(Optional.empty());

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("SECTOR ETF RELATIVE STRENGTH vs SPY"));
        assertTrue(message.contains("OUTPERFORMING SPY"));
        assertTrue(message.contains("UNDERPERFORMING SPY"));
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("Financials"));
        assertTrue(message.contains("Utilities"));
        assertTrue(message.contains("Real Estate"));
    }

    @Test
    void sendDailySectorRsSummary_withNoData_doesNotSendMessage() {
        // Given: No RS data available for any sector
        when(relativeStrengthService.getCurrentRsResult(anyString())).thenReturn(Optional.empty());

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void sendDailySectorRsSummary_allOutperforming_formatsCorrectly() {
        // Given: All sectors outperforming
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.03, 1.00, 50, true)));

        // Return empty for other sectors
        when(relativeStrengthService.getCurrentRsResult("XLE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLV")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLY")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLP")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLI")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLC")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLRE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLB")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLU")).thenReturn(Optional.empty());

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("OUTPERFORMING SPY"));
        assertFalse(message.contains("UNDERPERFORMING SPY"));
    }

    @Test
    void sendDailySectorRsSummary_allUnderperforming_formatsCorrectly() {
        // Given: All sectors underperforming
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(0.95, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(0.97, 1.00, 50, true)));

        // Return empty for other sectors
        when(relativeStrengthService.getCurrentRsResult("XLE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLV")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLY")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLP")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLI")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLC")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLRE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLB")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLU")).thenReturn(Optional.empty());

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertFalse(message.contains("OUTPERFORMING SPY"));
        assertTrue(message.contains("UNDERPERFORMING SPY"));
    }

    @Test
    void collectSectorRsData_sortsDescendingByPercentageDiff() {
        // Given
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.03, 1.00, 50, true))); // +3%
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.00, 50, true))); // +5%
        when(relativeStrengthService.getCurrentRsResult("XLU"))
                .thenReturn(Optional.of(new RsResult(0.98, 1.00, 50, true))); // -2%

        // Return empty for other sectors
        when(relativeStrengthService.getCurrentRsResult("XLE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLV")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLY")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLP")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLI")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLC")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLRE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLB")).thenReturn(Optional.empty());

        // When
        List<SectorRelativeStrengthTracker.SectorRsData> result = tracker.collectSectorRsData();

        // Then
        assertEquals(3, result.size());
        assertEquals("Financials", result.get(0).displayName()); // +5% first
        assertEquals("Technology", result.get(1).displayName()); // +3% second
        assertEquals("Utilities", result.get(2).displayName()); // -2% last
    }

    @Test
    void formatSummaryMessage_containsFooterExplanation() {
        // Given
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.02, 1.00, 50, true)));

        // Return empty for other sectors
        when(relativeStrengthService.getCurrentRsResult("XLF")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLV")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLY")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLP")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLI")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLC")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLRE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLB")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLU")).thenReturn(Optional.empty());

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("RS = Sector/SPY ratio"));
        assertTrue(message.contains("50-EMA"));
    }

    @Test
    void collectSectorRsData_handlesExceptionGracefully() {
        // Given: One sector throws exception
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenThrow(new RuntimeException("Test error"));
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.02, 1.00, 50, true)));

        // Return empty for other sectors
        when(relativeStrengthService.getCurrentRsResult("XLE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLV")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLY")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLP")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLI")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLC")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLRE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLB")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLU")).thenReturn(Optional.empty());

        // When
        List<SectorRelativeStrengthTracker.SectorRsData> result = tracker.collectSectorRsData();

        // Then: Should still return data for XLF
        assertEquals(1, result.size());
        assertEquals("Financials", result.getFirst().displayName());
    }

    @Test
    void formatSummaryMessage_displaysOnlySectorName_notEtfSymbol() {
        // Given
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.02, 1.00, 50, true)));

        // Return empty for other sectors
        when(relativeStrengthService.getCurrentRsResult("XLF")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLV")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLY")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLP")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLI")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLC")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLRE")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLB")).thenReturn(Optional.empty());
        when(relativeStrengthService.getCurrentRsResult("XLU")).thenReturn(Optional.empty());

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        // Should contain sector name
        assertTrue(message.contains("Technology"));
        // Should not contain ETF symbol in the list items (only in title)
        // The format should be "1. *Technology*: +2.0%" not "1. *XLK* (Technology): +2.0%"
        assertFalse(message.contains("*XLK*:"));
    }
}
