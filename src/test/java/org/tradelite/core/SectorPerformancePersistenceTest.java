package org.tradelite.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.core.SectorPerformancePersistence.PerformancePeriod;
import org.tradelite.repository.SectorPerformanceRepository;

@ExtendWith(MockitoExtension.class)
class SectorPerformancePersistenceTest {

    @Mock private SectorPerformanceRepository repository;

    private SectorPerformancePersistence persistence;

    @BeforeEach
    void setUp() {
        persistence = new SectorPerformancePersistence(repository);
    }

    @Test
    void saveSnapshot_shouldDelegateToRepository() {
        SectorPerformanceSnapshot snapshot = createTestSnapshot(LocalDate.now());

        persistence.saveSnapshot(snapshot);

        verify(repository).saveSnapshot(snapshot);
    }

    @Test
    void loadHistory_shouldQueryWithMaxHistoryDaysWindow() {
        when(repository.findSnapshotsSince(any())).thenReturn(List.of());

        persistence.loadHistory();

        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(repository).findSnapshotsSince(captor.capture());
        LocalDate since = captor.getValue();
        assertThat(
                since,
                is(LocalDate.now().minusDays(SectorPerformancePersistence.MAX_HISTORY_DAYS)));
    }

    @Test
    void loadHistory_shouldReturnSnapshotsFromRepository() {
        List<SectorPerformanceSnapshot> expected =
                List.of(
                        createTestSnapshot(LocalDate.now().minusDays(1)),
                        createTestSnapshot(LocalDate.now()));
        when(repository.findSnapshotsSince(any())).thenReturn(expected);

        List<SectorPerformanceSnapshot> result = persistence.loadHistory();

        assertThat(result, hasSize(2));
    }

    @Test
    void getLatestSnapshot_shouldDelegateToRepository() {
        SectorPerformanceSnapshot snapshot = createTestSnapshot(LocalDate.now());
        when(repository.findLatestSnapshot()).thenReturn(Optional.of(snapshot));

        Optional<SectorPerformanceSnapshot> result = persistence.getLatestSnapshot();

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().fetchDate(), is(LocalDate.now()));
    }

    @Test
    void getLatestSnapshot_shouldReturnEmptyWhenNoData() {
        when(repository.findLatestSnapshot()).thenReturn(Optional.empty());

        Optional<SectorPerformanceSnapshot> result = persistence.getLatestSnapshot();

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void getSnapshotForDate_shouldDelegateToRepository() {
        LocalDate date = LocalDate.of(2026, 4, 20);
        SectorPerformanceSnapshot snapshot = createTestSnapshot(date);
        when(repository.findByDate(date)).thenReturn(Optional.of(snapshot));

        Optional<SectorPerformanceSnapshot> result = persistence.getSnapshotForDate(date);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().fetchDate(), is(date));
    }

    @Test
    void getSnapshotForDate_shouldReturnEmptyWhenNotFound() {
        when(repository.findByDate(any())).thenReturn(Optional.empty());

        Optional<SectorPerformanceSnapshot> result =
                persistence.getSnapshotForDate(LocalDate.now());

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void getTopPerformers_shouldReturnEmptyListWhenNoData() {
        when(repository.findLatestSnapshot()).thenReturn(Optional.empty());

        List<IndustryPerformance> result = persistence.getTopPerformers(5, PerformancePeriod.DAILY);

        assertThat(result, is(empty()));
    }

    @Test
    void getTopPerformers_shouldReturnTopPerformersByDaily() {
        when(repository.findLatestSnapshot())
                .thenReturn(Optional.of(createTestSnapshot(LocalDate.now())));

        List<IndustryPerformance> result = persistence.getTopPerformers(2, PerformancePeriod.DAILY);

        assertThat(result, hasSize(2));
        assertThat(
                result.get(0).change().compareTo(result.get(1).change()), greaterThanOrEqualTo(0));
    }

    @Test
    void getTopPerformers_shouldReturnTopPerformersByWeekly() {
        when(repository.findLatestSnapshot())
                .thenReturn(Optional.of(createTestSnapshot(LocalDate.now())));

        List<IndustryPerformance> result =
                persistence.getTopPerformers(2, PerformancePeriod.WEEKLY);

        assertThat(result, hasSize(2));
        assertThat(
                result.get(0).perfWeek().compareTo(result.get(1).perfWeek()),
                greaterThanOrEqualTo(0));
    }

    @Test
    void getBottomPerformers_shouldReturnEmptyListWhenNoData() {
        when(repository.findLatestSnapshot()).thenReturn(Optional.empty());

        List<IndustryPerformance> result =
                persistence.getBottomPerformers(5, PerformancePeriod.DAILY);

        assertThat(result, is(empty()));
    }

    @Test
    void getBottomPerformers_shouldReturnBottomPerformersByDaily() {
        when(repository.findLatestSnapshot())
                .thenReturn(Optional.of(createTestSnapshot(LocalDate.now())));

        List<IndustryPerformance> result =
                persistence.getBottomPerformers(2, PerformancePeriod.DAILY);

        assertThat(result, hasSize(2));
        assertThat(result.get(0).change().compareTo(result.get(1).change()), lessThanOrEqualTo(0));
    }

    @Test
    void getTopPerformers_shouldHandleMonthlyPeriod() {
        when(repository.findLatestSnapshot())
                .thenReturn(Optional.of(createTestSnapshot(LocalDate.now())));

        List<IndustryPerformance> result =
                persistence.getTopPerformers(1, PerformancePeriod.MONTHLY);

        assertThat(result, hasSize(1));
    }

    @Test
    void getTopPerformers_shouldHandleQuarterlyPeriod() {
        when(repository.findLatestSnapshot())
                .thenReturn(Optional.of(createTestSnapshot(LocalDate.now())));

        List<IndustryPerformance> result =
                persistence.getTopPerformers(1, PerformancePeriod.QUARTERLY);

        assertThat(result, hasSize(1));
    }

    @Test
    void getTopPerformers_shouldHandleYearlyPeriod() {
        when(repository.findLatestSnapshot())
                .thenReturn(Optional.of(createTestSnapshot(LocalDate.now())));

        List<IndustryPerformance> result =
                persistence.getTopPerformers(1, PerformancePeriod.YEARLY);

        assertThat(result, hasSize(1));
    }

    private SectorPerformanceSnapshot createTestSnapshot(LocalDate date) {
        List<IndustryPerformance> performances =
                List.of(
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
                                new BigDecimal("-1.25")),
                        new IndustryPerformance(
                                "Finance",
                                new BigDecimal("1.50"),
                                new BigDecimal("3.00"),
                                new BigDecimal("6.00"),
                                new BigDecimal("12.00"),
                                new BigDecimal("18.00"),
                                new BigDecimal("4.00"),
                                new BigDecimal("0.75")));
        return new SectorPerformanceSnapshot(date, performances);
    }
}
