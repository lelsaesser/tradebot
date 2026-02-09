package org.tradelite.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.finviz.dto.IndustryPerformance;

@Slf4j
@Component
public class SectorPerformancePersistence {

    private static final String FILE_PATH = "config/sector-performance.json";
    private static final int MAX_HISTORY_DAYS = 60;

    private final ObjectMapper objectMapper;

    public SectorPerformancePersistence(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void saveSnapshot(SectorPerformanceSnapshot snapshot) throws IOException {
        List<SectorPerformanceSnapshot> history = loadHistory();

        history.removeIf(s -> s.fetchDate().equals(snapshot.fetchDate()));
        history.add(snapshot);

        while (history.size() > MAX_HISTORY_DAYS) {
            history.removeFirst();
        }

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(FILE_PATH), history);
        log.info(
                "Saved sector performance snapshot for {} with {} industries",
                snapshot.fetchDate(),
                snapshot.performances().size());
    }

    public List<SectorPerformanceSnapshot> loadHistory() {
        File file = new File(FILE_PATH);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(
                    file, new TypeReference<List<SectorPerformanceSnapshot>>() {});
        } catch (IOException e) {
            log.warn("Failed to load sector performance history: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    public Optional<SectorPerformanceSnapshot> getLatestSnapshot() {
        List<SectorPerformanceSnapshot> history = loadHistory();
        if (history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.getLast());
    }

    public Optional<SectorPerformanceSnapshot> getSnapshotForDate(LocalDate date) {
        return loadHistory().stream().filter(s -> s.fetchDate().equals(date)).findFirst();
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
