package org.tradelite.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
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
    private static final String JSON_FILE_PATH = "config/sector-performance.json";

    private final SectorPerformanceRepository repository;
    private final ObjectMapper objectMapper;

    public SectorPerformancePersistence(
            SectorPerformanceRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void migrateJsonDataIfNeeded() {
        File jsonFile = new File(JSON_FILE_PATH);
        if (!jsonFile.exists()) {
            return;
        }

        Optional<SectorPerformanceSnapshot> existing = repository.findLatestSnapshot();
        if (existing.isPresent()) {
            return;
        }

        try {
            List<SectorPerformanceSnapshot> history =
                    objectMapper.readValue(jsonFile, new TypeReference<>() {});
            for (SectorPerformanceSnapshot snapshot : history) {
                repository.saveSnapshot(snapshot);
            }
            log.info(
                    "Migrated {} sector performance snapshots from JSON to SQLite", history.size());
        } catch (IOException e) {
            log.warn("Failed to migrate sector performance JSON data: {}", e.getMessage());
        }
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
