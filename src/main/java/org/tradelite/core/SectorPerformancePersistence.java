package org.tradelite.core;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.repository.SectorPerformanceRepository;

@Slf4j
@Component
public class SectorPerformancePersistence {

    static final int MAX_HISTORY_DAYS = 365;

    private final SectorPerformanceRepository repository;

    public SectorPerformancePersistence(SectorPerformanceRepository repository) {
        this.repository = repository;
    }

    public void saveSnapshot(SectorPerformanceSnapshot snapshot) {
        repository.saveSnapshot(snapshot);
        log.info(
                "Saved sector performance snapshot for {} with {} industries",
                snapshot.fetchDate(),
                snapshot.performances().size());
    }

    public List<SectorPerformanceSnapshot> loadHistory() {
        return repository.findSnapshotsSince(LocalDate.now().minusDays(MAX_HISTORY_DAYS));
    }

    public Optional<SectorPerformanceSnapshot> getLatestSnapshot() {
        return repository.findLatestSnapshot();
    }

    public Optional<SectorPerformanceSnapshot> getSnapshotForDate(LocalDate date) {
        return repository.findByDate(date);
    }

    public List<IndustryPerformance> getTopPerformers(int n, PerformancePeriod period) {
        return getLatestSnapshot()
                .map(
                        snapshot ->
                                snapshot.performances().stream()
                                        .sorted(
                                                (a, b) ->
                                                        getPerformanceValue(b, period)
                                                                .compareTo(
                                                                        getPerformanceValue(
                                                                                a, period)))
                                        .limit(n)
                                        .toList())
                .orElse(List.of());
    }

    public List<IndustryPerformance> getBottomPerformers(int n, PerformancePeriod period) {
        return getLatestSnapshot()
                .map(
                        snapshot ->
                                snapshot.performances().stream()
                                        .sorted(
                                                (a, b) ->
                                                        getPerformanceValue(a, period)
                                                                .compareTo(
                                                                        getPerformanceValue(
                                                                                b, period)))
                                        .limit(n)
                                        .toList())
                .orElse(List.of());
    }

    private java.math.BigDecimal getPerformanceValue(
            IndustryPerformance perf, PerformancePeriod period) {
        return switch (period) {
            case DAILY -> perf.change();
            case WEEKLY -> perf.perfWeek();
            case MONTHLY -> perf.perfMonth();
            case QUARTERLY -> perf.perfQuarter();
            case YEARLY -> perf.perfYear();
        };
    }

    public enum PerformancePeriod {
        DAILY,
        WEEKLY,
        MONTHLY,
        QUARTERLY,
        YEARLY
    }
}
