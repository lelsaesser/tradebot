package org.tradelite.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finviz.FinvizClient;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.core.SectorPerformancePersistence.PerformancePeriod;

@ExtendWith(MockitoExtension.class)
class SectorRotationTrackerTest {

    @Mock private FinvizClient finvizClient;
    @Mock private SectorPerformancePersistence persistence;
    @Mock private TelegramClient telegramClient;

    private SectorRotationTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new SectorRotationTracker(finvizClient, persistence, telegramClient);
    }

    @Test
    void fetchAndStoreDailyPerformance_shouldFetchAndSaveData() throws IOException {
        List<IndustryPerformance> performances = createTestPerformances();
        when(finvizClient.fetchIndustryPerformance()).thenReturn(performances);

        tracker.fetchAndStoreDailyPerformance();

        verify(finvizClient).fetchIndustryPerformance();
        verify(persistence).saveSnapshot(any(SectorPerformanceSnapshot.class));
    }

    @Test
    void fetchAndStoreDailyPerformance_shouldSkipWhenNoData() throws IOException {
        when(finvizClient.fetchIndustryPerformance()).thenReturn(List.of());

        tracker.fetchAndStoreDailyPerformance();

        verify(finvizClient).fetchIndustryPerformance();
        verify(persistence, never()).saveSnapshot(any());
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void fetchAndStoreDailyPerformance_shouldHandleIOException() throws IOException {
        when(finvizClient.fetchIndustryPerformance()).thenThrow(new IOException("Network error"));

        tracker.fetchAndStoreDailyPerformance();

        verify(finvizClient).fetchIndustryPerformance();
        verify(persistence, never()).saveSnapshot(any());
    }

    @Test
    void fetchAndStoreDailyPerformance_shouldSendDailySummary() throws IOException {
        List<IndustryPerformance> performances = createTestPerformances();
        when(finvizClient.fetchIndustryPerformance()).thenReturn(performances);
        when(persistence.getTopPerformers(5, PerformancePeriod.DAILY)).thenReturn(performances);
        when(persistence.getBottomPerformers(5, PerformancePeriod.DAILY)).thenReturn(performances);
        when(persistence.getTopPerformers(5, PerformancePeriod.WEEKLY)).thenReturn(performances);
        when(persistence.getBottomPerformers(5, PerformancePeriod.WEEKLY)).thenReturn(performances);

        tracker.fetchAndStoreDailyPerformance();

        verify(telegramClient).sendMessage(contains("Daily Sector Rotation Report"));
    }

    @Test
    void sendDailySummary_shouldFormatReportCorrectly() {
        List<IndustryPerformance> topPerformers =
                List.of(
                        new IndustryPerformance(
                                "Technology",
                                new BigDecimal("5.25"),
                                new BigDecimal("10.50"),
                                new BigDecimal("15.75"),
                                new BigDecimal("-2.30"),
                                new BigDecimal("25.00"),
                                new BigDecimal("8.50"),
                                new BigDecimal("2.75")));
        List<IndustryPerformance> bottomPerformers =
                List.of(
                        new IndustryPerformance(
                                "Healthcare",
                                new BigDecimal("-3.25"),
                                new BigDecimal("-7.50"),
                                new BigDecimal("-12.00"),
                                new BigDecimal("-15.25"),
                                new BigDecimal("-20.00"),
                                new BigDecimal("-5.50"),
                                new BigDecimal("-1.25")));

        when(persistence.getTopPerformers(5, PerformancePeriod.DAILY)).thenReturn(topPerformers);
        when(persistence.getBottomPerformers(5, PerformancePeriod.DAILY))
                .thenReturn(bottomPerformers);
        when(persistence.getTopPerformers(5, PerformancePeriod.WEEKLY)).thenReturn(topPerformers);
        when(persistence.getBottomPerformers(5, PerformancePeriod.WEEKLY))
                .thenReturn(bottomPerformers);

        tracker.sendDailySummary();

        verify(telegramClient).sendMessage(contains("Top 5 Daily Performers"));
        verify(telegramClient).sendMessage(contains("Bottom 5 Daily Performers"));
        verify(telegramClient).sendMessage(contains("Top 5 Weekly Performers"));
        verify(telegramClient).sendMessage(contains("Bottom 5 Weekly Performers"));
    }

    @Test
    void generateSectorReport_shouldReturnFormattedReport() {
        List<IndustryPerformance> topPerformers =
                List.of(
                        new IndustryPerformance(
                                "Technology",
                                new BigDecimal("5.25"),
                                new BigDecimal("10.50"),
                                new BigDecimal("15.75"),
                                new BigDecimal("-2.30"),
                                new BigDecimal("25.00"),
                                new BigDecimal("8.50"),
                                new BigDecimal("2.75")));
        List<IndustryPerformance> bottomPerformers =
                List.of(
                        new IndustryPerformance(
                                "Healthcare",
                                new BigDecimal("-3.25"),
                                new BigDecimal("-7.50"),
                                new BigDecimal("-12.00"),
                                new BigDecimal("-15.25"),
                                new BigDecimal("-20.00"),
                                new BigDecimal("-5.50"),
                                new BigDecimal("-1.25")));

        when(persistence.getTopPerformers(10, PerformancePeriod.DAILY)).thenReturn(topPerformers);
        when(persistence.getBottomPerformers(10, PerformancePeriod.DAILY))
                .thenReturn(bottomPerformers);

        String report = tracker.generateSectorReport();

        assertThat(report.contains("Sector Performance Overview"), is(true));
        assertThat(report.contains("Technology"), is(true));
        assertThat(report.contains("Healthcare"), is(true));
    }

    @Test
    void sendDailySummary_shouldHandleEmptyPerformers() {
        when(persistence.getTopPerformers(5, PerformancePeriod.DAILY)).thenReturn(List.of());
        when(persistence.getBottomPerformers(5, PerformancePeriod.DAILY)).thenReturn(List.of());
        when(persistence.getTopPerformers(5, PerformancePeriod.WEEKLY)).thenReturn(List.of());
        when(persistence.getBottomPerformers(5, PerformancePeriod.WEEKLY)).thenReturn(List.of());

        tracker.sendDailySummary();

        verify(telegramClient).sendMessage(anyString());
    }

    private List<IndustryPerformance> createTestPerformances() {
        return List.of(
                new IndustryPerformance(
                        "Technology",
                        new BigDecimal("5.25"),
                        new BigDecimal("10.50"),
                        new BigDecimal("15.75"),
                        new BigDecimal("-2.30"),
                        new BigDecimal("25.00"),
                        new BigDecimal("8.50"),
                        new BigDecimal("2.75")),
                new IndustryPerformance(
                        "Healthcare",
                        new BigDecimal("-3.25"),
                        new BigDecimal("-7.50"),
                        new BigDecimal("-12.00"),
                        new BigDecimal("-15.25"),
                        new BigDecimal("-20.00"),
                        new BigDecimal("-5.50"),
                        new BigDecimal("-1.25")));
    }
}
