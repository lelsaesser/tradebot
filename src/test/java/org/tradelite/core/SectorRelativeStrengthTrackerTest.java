package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;

@ExtendWith(MockitoExtension.class)
class SectorRelativeStrengthTrackerTest {

    @Mock private RelativeStrengthService relativeStrengthService;

    @Mock private TelegramGateway telegramClient;

    @Mock private SectorRsStreakPersistence streakPersistence;

    @Mock private SymbolRegistry symbolRegistry;

    private SectorRelativeStrengthTracker tracker;

    @BeforeEach
    void setUp() {
        tracker =
                new SectorRelativeStrengthTracker(
                        relativeStrengthService, telegramClient, streakPersistence, symbolRegistry);

        Map<String, String> etfs = new LinkedHashMap<>(SymbolRegistry.BROAD_SECTOR_ETFS);
        etfs.putAll(SymbolRegistry.THEMATIC_ETFS);
        lenient().when(symbolRegistry.getAllEtfs()).thenReturn(etfs);
        lenient()
                .when(symbolRegistry.getThematicSymbols())
                .thenReturn(SymbolRegistry.THEMATIC_ETFS.keySet());

        // Default stubs so un-mocked ETFs (including thematic) return empty instead of null
        lenient()
                .when(relativeStrengthService.calculateRelativeStrength(anyString(), anyString()))
                .thenReturn(Optional.empty());
        lenient()
                .when(relativeStrengthService.getCurrentRsResult(anyString()))
                .thenReturn(Optional.empty());
    }

    @Test
    void analyzeAndSendAlerts_noSignals_noMessageSent() {
        // Given: No crossover signals
        when(relativeStrengthService.calculateRelativeStrength(anyString(), anyString()))
                .thenReturn(Optional.empty());

        // When
        tracker.analyzeAndSendAlerts();

        // Then
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void analyzeAndSendAlerts_outperformingSignal_sendsAlert() {
        // Given: One sector outperforming
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "XLK",
                        "Technology",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.05,
                        1.00,
                        2.5);
        when(relativeStrengthService.calculateRelativeStrength("XLK", "Technology"))
                .thenReturn(Optional.of(signal));

        // When
        tracker.analyzeAndSendAlerts();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("SECTOR RS CROSSOVER ALERT"));
        assertTrue(message.contains("NOW OUTPERFORMING SPY"));
        assertTrue(message.contains("Technology"));
    }

    @Test
    void analyzeAndSendAlerts_underperformingSignal_sendsAlert() {
        // Given: One sector underperforming
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "XLU",
                        "Utilities",
                        RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                        0.95,
                        1.00,
                        -3.5);
        when(relativeStrengthService.calculateRelativeStrength("XLU", "Utilities"))
                .thenReturn(Optional.of(signal));

        // When
        tracker.analyzeAndSendAlerts();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("NOW UNDERPERFORMING SPY"));
        assertTrue(message.contains("Utilities"));
    }

    @Test
    void analyzeAndSendAlerts_mixedSignals_sendsGroupedAlert() {
        // Given: Mixed signals
        RelativeStrengthSignal outperforming =
                new RelativeStrengthSignal(
                        "XLK",
                        "Technology",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.05,
                        1.00,
                        2.5);
        RelativeStrengthSignal underperforming =
                new RelativeStrengthSignal(
                        "XLU",
                        "Utilities",
                        RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                        0.95,
                        1.00,
                        -3.5);

        when(relativeStrengthService.calculateRelativeStrength("XLK", "Technology"))
                .thenReturn(Optional.of(outperforming));
        when(relativeStrengthService.calculateRelativeStrength("XLU", "Utilities"))
                .thenReturn(Optional.of(underperforming));

        // When
        tracker.analyzeAndSendAlerts();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("NOW OUTPERFORMING SPY"));
        assertTrue(message.contains("NOW UNDERPERFORMING SPY"));
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("Utilities"));
    }

    @Test
    void analyzeAndSendAlerts_exceptionInOneSector_continuesProcessing() {
        // Given: One sector throws exception
        when(relativeStrengthService.calculateRelativeStrength("XLK", "Technology"))
                .thenThrow(new RuntimeException("API Error"));
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "XLF",
                        "Financials",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.03,
                        1.00,
                        1.5);
        when(relativeStrengthService.calculateRelativeStrength("XLF", "Financials"))
                .thenReturn(Optional.of(signal));

        // When
        tracker.analyzeAndSendAlerts();

        // Then: Should still process other sectors
        verify(telegramClient).sendMessage(contains("Financials"));
    }

    @Test
    void formatCrossoverAlertMessage_outperformingOnly_correctFormat() {
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "XLK",
                        "Technology",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.05,
                        1.00,
                        2.5);

        String message = tracker.formatCrossoverAlertMessage(List.of(signal), List.of());

        assertTrue(message.contains("SECTOR RS CROSSOVER ALERT"));
        assertTrue(message.contains("NOW OUTPERFORMING SPY"));
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("XLK"));
        assertTrue(message.contains("+2.5%"));
        assertFalse(message.contains("NOW UNDERPERFORMING SPY"));
    }

    @Test
    void formatCrossoverAlertMessage_underperformingOnly_correctFormat() {
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "XLU",
                        "Utilities",
                        RelativeStrengthSignal.SignalType.UNDERPERFORMING,
                        0.95,
                        1.00,
                        -3.5);

        String message = tracker.formatCrossoverAlertMessage(List.of(), List.of(signal));

        assertTrue(message.contains("NOW UNDERPERFORMING SPY"));
        assertTrue(message.contains("Utilities"));
        assertTrue(message.contains("-3.5%"));
        assertFalse(message.contains("NOW OUTPERFORMING SPY"));
    }

    @Test
    void formatCrossoverAlertMessage_containsFooter() {
        RelativeStrengthSignal signal =
                new RelativeStrengthSignal(
                        "XLK",
                        "Technology",
                        RelativeStrengthSignal.SignalType.OUTPERFORMING,
                        1.05,
                        1.00,
                        2.5);

        String message = tracker.formatCrossoverAlertMessage(List.of(signal), List.of());

        assertTrue(message.contains("RS crossed 50-period EMA"));
    }

    @Test
    void sendDailySectorRsSummary_withMixedPerformance_sendsFormattedMessage() {
        // Given: Some sectors outperforming, some underperforming
        // Lenient default in setUp returns Optional.empty() for all un-mocked ETFs
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.02, 50, true))); // +2.94%
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.01, 1.00, 50, true))); // +1.0%
        when(relativeStrengthService.getCurrentRsResult("XLU"))
                .thenReturn(Optional.of(new RsResult(0.95, 1.00, 50, true))); // -5.0%
        when(relativeStrengthService.getCurrentRsResult("XLRE"))
                .thenReturn(Optional.of(new RsResult(0.98, 1.00, 50, true))); // -2.0%

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenAnswer(
                        invocation -> {
                            String symbol = invocation.getArgument(0);
                            boolean isOutperforming = invocation.getArgument(1);
                            LocalDate date = invocation.getArgument(2);
                            return new SectorRsStreakPersistence.StreakUpdateResult(
                                    new SectorRsStreak(symbol, 3, isOutperforming, date), 0, false);
                        });

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("Sector ETF Relative Strength vs SPY"));
        assertTrue(message.contains("Outperforming SPY"));
        assertTrue(message.contains("Underperforming SPY"));
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("Financials"));
        assertTrue(message.contains("Utilities"));
        assertTrue(message.contains("Real Estate"));
        assertTrue(message.contains("📅")); // Streak indicator
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
        // Lenient default in setUp returns Optional.empty() for all un-mocked ETFs
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.03, 1.00, 50, true)));

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenAnswer(
                        invocation -> {
                            String symbol = invocation.getArgument(0);
                            boolean isOutperforming = invocation.getArgument(1);
                            LocalDate date = invocation.getArgument(2);
                            return new SectorRsStreakPersistence.StreakUpdateResult(
                                    new SectorRsStreak(symbol, 1, isOutperforming, date), 0, false);
                        });

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("Outperforming SPY"));
        assertFalse(message.contains("Underperforming SPY"));
    }

    @Test
    void sendDailySectorRsSummary_allUnderperforming_formatsCorrectly() {
        // Given: All sectors underperforming
        // Lenient default in setUp returns Optional.empty() for all un-mocked ETFs
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(0.95, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(0.97, 1.00, 50, true)));

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenAnswer(
                        invocation -> {
                            String symbol = invocation.getArgument(0);
                            boolean isOutperforming = invocation.getArgument(1);
                            LocalDate date = invocation.getArgument(2);
                            return new SectorRsStreakPersistence.StreakUpdateResult(
                                    new SectorRsStreak(symbol, 1, isOutperforming, date), 0, false);
                        });

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertFalse(message.contains("Outperforming SPY"));
        assertTrue(message.contains("Underperforming SPY"));
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

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenAnswer(
                        invocation -> {
                            String symbol = invocation.getArgument(0);
                            boolean isOutperforming = invocation.getArgument(1);
                            LocalDate date = invocation.getArgument(2);
                            return new SectorRsStreakPersistence.StreakUpdateResult(
                                    new SectorRsStreak(symbol, 1, isOutperforming, date), 0, false);
                        });

        // When
        List<SectorRsData> result = tracker.collectSectorRsData();

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

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenReturn(
                        new SectorRsStreakPersistence.StreakUpdateResult(
                                new SectorRsStreak("XLK", 1, true, LocalDate.now()), 0, false));

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("RS = Sector/SPY ratio"));
        assertTrue(message.contains("50-EMA"));
        assertTrue(message.contains("streak days"));
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

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenReturn(
                        new SectorRsStreakPersistence.StreakUpdateResult(
                                new SectorRsStreak("XLF", 1, true, LocalDate.now()), 0, false));

        // When
        List<SectorRsData> result = tracker.collectSectorRsData();

        // Then: Should still return data for XLF
        assertEquals(1, result.size());
        assertEquals("Financials", result.getFirst().displayName());
    }

    @Test
    void formatSummaryMessage_displaysStreakDays() {
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

        // Mock streak persistence - return 5 day streak
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenReturn(
                        new SectorRsStreakPersistence.StreakUpdateResult(
                                new SectorRsStreak("XLK", 5, true, LocalDate.now()), 0, false));

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("📅5")); // 5 day streak
    }

    @Test
    void sendDailySectorRsSummary_incompleteData_showsDaysCount() {
        // Given: Sector with incomplete data (less than 50 data points)
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(
                        Optional.of(new RsResult(1.02, 1.00, 32, false))); // 32 days, incomplete

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

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenReturn(
                        new SectorRsStreakPersistence.StreakUpdateResult(
                                new SectorRsStreak("XLK", 1, true, LocalDate.now()), 0, false));

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("32 days"));
        assertTrue(message.contains("Technology"));
    }

    @Test
    void sendDailySectorRsSummary_mixedCompleteAndIncomplete_formatsCorrectly() {
        // Given: One complete and one incomplete sector
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.00, 50, true))); // Complete
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.03, 1.00, 25, false))); // Incomplete

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

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenAnswer(
                        invocation -> {
                            String symbol = invocation.getArgument(0);
                            boolean isOutperforming = invocation.getArgument(1);
                            LocalDate date = invocation.getArgument(2);
                            return new SectorRsStreakPersistence.StreakUpdateResult(
                                    new SectorRsStreak(symbol, 1, isOutperforming, date), 0, false);
                        });

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        // Complete sector should not show days
        assertTrue(message.contains("Technology"));
        // Incomplete sector should show days
        assertTrue(message.contains("25 days"));
        assertTrue(message.contains("Financials"));
    }

    @Test
    void collectSectorRsData_returnsEmptyListWhenNoDataAvailable() {
        // Given: All sectors return empty
        when(relativeStrengthService.getCurrentRsResult(anyString())).thenReturn(Optional.empty());

        // When
        List<SectorRsData> result = tracker.collectSectorRsData();

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void formatSummaryMessage_withZeroPercentage_showsPlusSign() {
        // Given: Sector with exactly 0% difference
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.00, 1.00, 50, true))); // RS = EMA, 0%

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

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenReturn(
                        new SectorRsStreakPersistence.StreakUpdateResult(
                                new SectorRsStreak("XLK", 1, true, LocalDate.now()), 0, false));

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        assertTrue(message.contains("+0.0%") || message.contains("0.0%"));
    }

    @Test
    void formatSummaryMessage_allSectors_includesAllSectorNames() {
        // Given: All 11 sectors have data
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLF"))
                .thenReturn(Optional.of(new RsResult(1.04, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLE"))
                .thenReturn(Optional.of(new RsResult(1.03, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLV"))
                .thenReturn(Optional.of(new RsResult(1.02, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLY"))
                .thenReturn(Optional.of(new RsResult(1.01, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLP"))
                .thenReturn(Optional.of(new RsResult(0.99, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLI"))
                .thenReturn(Optional.of(new RsResult(0.98, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLC"))
                .thenReturn(Optional.of(new RsResult(0.97, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLRE"))
                .thenReturn(Optional.of(new RsResult(0.96, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLB"))
                .thenReturn(Optional.of(new RsResult(0.95, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLU"))
                .thenReturn(Optional.of(new RsResult(0.94, 1.00, 50, true)));

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenAnswer(
                        invocation -> {
                            String symbol = invocation.getArgument(0);
                            boolean isOutperforming = invocation.getArgument(1);
                            LocalDate date = invocation.getArgument(2);
                            return new SectorRsStreakPersistence.StreakUpdateResult(
                                    new SectorRsStreak(symbol, 1, isOutperforming, date), 0, false);
                        });

        // When
        tracker.sendDailySectorRsSummary();

        // Then
        ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(messageCaptor.capture());

        String message = messageCaptor.getValue();
        // Check all sector names are present
        assertTrue(message.contains("Technology"));
        assertTrue(message.contains("Financials"));
        assertTrue(message.contains("Energy"));
        assertTrue(message.contains("Health Care"));
        assertTrue(message.contains("Cons. Discretionary"));
        assertTrue(message.contains("Cons. Staples"));
        assertTrue(message.contains("Industrials"));
        assertTrue(message.contains("Communication"));
        assertTrue(message.contains("Real Estate"));
        assertTrue(message.contains("Materials"));
        assertTrue(message.contains("Utilities"));
    }

    @Test
    void sectorRsData_recordFieldsIncludeStreakDays() {
        // Given
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.02, 45, false)));

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

        // Mock streak persistence with 7 day streak
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenReturn(
                        new SectorRsStreakPersistence.StreakUpdateResult(
                                new SectorRsStreak("XLK", 7, true, LocalDate.now()), 0, false));

        // When
        List<SectorRsData> result = tracker.collectSectorRsData();

        // Then
        assertEquals(1, result.size());
        SectorRsData data = result.getFirst();
        assertEquals("XLK", data.symbol());
        assertEquals("Technology", data.displayName());
        assertEquals(1.05, data.rsValue(), 0.001);
        assertEquals(1.02, data.emaValue(), 0.001);
        assertEquals(45, data.dataPoints());
        assertFalse(data.isComplete());
        assertEquals(7, data.streakDays());
        assertEquals(0, data.previousStreakDays());
        assertFalse(data.streakJustEnded());
    }

    @Test
    void collectSectorRsData_updatesStreakPersistence() {
        // Given
        when(relativeStrengthService.getCurrentRsResult("XLK"))
                .thenReturn(Optional.of(new RsResult(1.05, 1.00, 50, true)));
        when(relativeStrengthService.getCurrentRsResult("XLU"))
                .thenReturn(Optional.of(new RsResult(0.95, 1.00, 50, true)));

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

        // Mock streak persistence
        when(streakPersistence.updateStreak(anyString(), anyBoolean(), any(LocalDate.class)))
                .thenAnswer(
                        invocation -> {
                            String symbol = invocation.getArgument(0);
                            boolean isOutperforming = invocation.getArgument(1);
                            LocalDate date = invocation.getArgument(2);
                            return new SectorRsStreakPersistence.StreakUpdateResult(
                                    new SectorRsStreak(symbol, 1, isOutperforming, date), 0, false);
                        });

        // When
        tracker.collectSectorRsData();

        // Then - verify streak persistence was called with correct parameters
        verify(streakPersistence).updateStreak(eq("XLK"), eq(true), any(LocalDate.class));
        verify(streakPersistence).updateStreak(eq("XLU"), eq(false), any(LocalDate.class));
    }
}
