package org.tradelite.core;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.core.SectorPerformancePersistence.PerformancePeriod;

class SectorPerformancePersistenceTest {

    private static final String TEST_FILE_PATH = "config/sector-performance.json";
    private SectorPerformancePersistence persistence;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        persistence = new SectorPerformancePersistence(objectMapper);
    }

    @AfterEach
    void tearDown() {
        File file = new File(TEST_FILE_PATH);
        if (file.exists()) {
            file.delete();
        }
    }

    @Test
    void saveSnapshot_shouldSaveSnapshotToFile() throws IOException {
        SectorPerformanceSnapshot snapshot = createTestSnapshot(LocalDate.now());

        persistence.saveSnapshot(snapshot);

        File file = new File(TEST_FILE_PATH);
        assertThat(file.exists(), is(true));
    }

    @Test
    void loadHistory_shouldReturnEmptyListWhenFileDoesNotExist() {
        List<SectorPerformanceSnapshot> history = persistence.loadHistory();

        assertThat(history, is(empty()));
    }

    @Test
    void loadHistory_shouldReturnSavedSnapshots() throws IOException {
        SectorPerformanceSnapshot snapshot = createTestSnapshot(LocalDate.now());
        persistence.saveSnapshot(snapshot);

        List<SectorPerformanceSnapshot> history = persistence.loadHistory();

        assertThat(history, hasSize(1));
        assertThat(history.getFirst().fetchDate(), is(LocalDate.now()));
    }

    @Test
    void saveSnapshot_shouldReplaceSnapshotForSameDate() throws IOException {
        LocalDate today = LocalDate.now();
        SectorPerformanceSnapshot snapshot1 = createTestSnapshot(today);
        SectorPerformanceSnapshot snapshot2 = createTestSnapshotWithDifferentData(today);

        persistence.saveSnapshot(snapshot1);
        persistence.saveSnapshot(snapshot2);

        List<SectorPerformanceSnapshot> history = persistence.loadHistory();
        assertThat(history, hasSize(1));
    }

    @Test
    void getLatestSnapshot_shouldReturnEmptyWhenNoData() {
        Optional<SectorPerformanceSnapshot> result = persistence.getLatestSnapshot();

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void getLatestSnapshot_shouldReturnLatestSnapshot() throws IOException {
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now().minusDays(1)));
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now()));

        Optional<SectorPerformanceSnapshot> result = persistence.getLatestSnapshot();

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().fetchDate(), is(LocalDate.now()));
    }

    @Test
    void getSnapshotForDate_shouldReturnEmptyWhenNotFound() {
        Optional<SectorPerformanceSnapshot> result =
                persistence.getSnapshotForDate(LocalDate.now());

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void getSnapshotForDate_shouldReturnSnapshotForDate() throws IOException {
        LocalDate targetDate = LocalDate.now().minusDays(5);
        persistence.saveSnapshot(createTestSnapshot(targetDate));

        Optional<SectorPerformanceSnapshot> result = persistence.getSnapshotForDate(targetDate);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().fetchDate(), is(targetDate));
    }

    @Test
    void getTopPerformers_shouldReturnEmptyListWhenNoData() {
        List<IndustryPerformance> result = persistence.getTopPerformers(5, PerformancePeriod.DAILY);

        assertThat(result, is(empty()));
    }

    @Test
    void getTopPerformers_shouldReturnTopPerformersByDaily() throws IOException {
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now()));

        List<IndustryPerformance> result = persistence.getTopPerformers(2, PerformancePeriod.DAILY);

        assertThat(result, hasSize(2));
        assertThat(
                result.get(0).change().compareTo(result.get(1).change()), greaterThanOrEqualTo(0));
    }

    @Test
    void getTopPerformers_shouldReturnTopPerformersByWeekly() throws IOException {
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now()));

        List<IndustryPerformance> result =
                persistence.getTopPerformers(2, PerformancePeriod.WEEKLY);

        assertThat(result, hasSize(2));
        assertThat(
                result.get(0).perfWeek().compareTo(result.get(1).perfWeek()),
                greaterThanOrEqualTo(0));
    }

    @Test
    void getBottomPerformers_shouldReturnEmptyListWhenNoData() {
        List<IndustryPerformance> result =
                persistence.getBottomPerformers(5, PerformancePeriod.DAILY);

        assertThat(result, is(empty()));
    }

    @Test
    void getBottomPerformers_shouldReturnBottomPerformersByDaily() throws IOException {
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now()));

        List<IndustryPerformance> result =
                persistence.getBottomPerformers(2, PerformancePeriod.DAILY);

        assertThat(result, hasSize(2));
        assertThat(result.get(0).change().compareTo(result.get(1).change()), lessThanOrEqualTo(0));
    }

    @Test
    void getTopPerformers_shouldHandleMonthlyPeriod() throws IOException {
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now()));

        List<IndustryPerformance> result =
                persistence.getTopPerformers(1, PerformancePeriod.MONTHLY);

        assertThat(result, hasSize(1));
    }

    @Test
    void getTopPerformers_shouldHandleQuarterlyPeriod() throws IOException {
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now()));

        List<IndustryPerformance> result =
                persistence.getTopPerformers(1, PerformancePeriod.QUARTERLY);

        assertThat(result, hasSize(1));
    }

    @Test
    void getTopPerformers_shouldHandleYearlyPeriod() throws IOException {
        persistence.saveSnapshot(createTestSnapshot(LocalDate.now()));

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

    private SectorPerformanceSnapshot createTestSnapshotWithDifferentData(LocalDate date) {
        List<IndustryPerformance> performances =
                List.of(
                        new IndustryPerformance(
                                "Energy",
                                new BigDecimal("8.00"),
                                new BigDecimal("12.00"),
                                new BigDecimal("18.00"),
                                new BigDecimal("24.00"),
                                new BigDecimal("30.00"),
                                new BigDecimal("10.00"),
                                new BigDecimal("4.00")));
        return new SectorPerformanceSnapshot(date, performances);
    }
}
