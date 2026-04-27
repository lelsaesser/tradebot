package org.tradelite.repository;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.tradelite.client.finviz.dto.IndustryPerformance;
import org.tradelite.core.SectorPerformanceSnapshot;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteSectorPerformanceRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteSectorPerformanceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteSectorPerformanceRepository(jdbcTemplate);
    }

    @Test
    void saveSnapshot_persistsRows() {
        SectorPerformanceSnapshot snapshot = createSnapshot(LocalDate.of(2026, 4, 25));

        repository.saveSnapshot(snapshot);

        Optional<SectorPerformanceSnapshot> result =
                repository.findByDate(LocalDate.of(2026, 4, 25));
        assertThat(result.isPresent(), is(true));
        assertThat(result.get().performances(), hasSize(3));
    }

    @Test
    void saveSnapshot_sameDateReplacesExistingRows() {
        LocalDate date = LocalDate.of(2026, 4, 25);
        repository.saveSnapshot(createSnapshot(date));

        List<IndustryPerformance> newPerformances =
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
        repository.saveSnapshot(new SectorPerformanceSnapshot(date, newPerformances));

        Optional<SectorPerformanceSnapshot> result = repository.findByDate(date);
        assertThat(result.isPresent(), is(true));
        assertThat(result.get().performances(), hasSize(1));
        assertThat(result.get().performances().getFirst().name(), is("Energy"));
    }

    @Test
    void findByDate_returnsAssembledSnapshot() {
        LocalDate date = LocalDate.of(2026, 4, 25);
        repository.saveSnapshot(createSnapshot(date));

        Optional<SectorPerformanceSnapshot> result = repository.findByDate(date);

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().fetchDate(), is(date));

        IndustryPerformance tech =
                result.get().performances().stream()
                        .filter(p -> "Technology".equals(p.name()))
                        .findFirst()
                        .orElseThrow();
        assertThat(tech.perfWeek(), is(new BigDecimal("5.25")));
        assertThat(tech.change(), is(new BigDecimal("2.75")));
    }

    @Test
    void findByDate_nonexistentDateReturnsEmpty() {
        Optional<SectorPerformanceSnapshot> result =
                repository.findByDate(LocalDate.of(2026, 1, 1));

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void findLatestSnapshot_returnsNewestDate() {
        repository.saveSnapshot(createSnapshot(LocalDate.of(2026, 4, 23)));
        repository.saveSnapshot(createSnapshot(LocalDate.of(2026, 4, 25)));
        repository.saveSnapshot(createSnapshot(LocalDate.of(2026, 4, 24)));

        Optional<SectorPerformanceSnapshot> result = repository.findLatestSnapshot();

        assertThat(result.isPresent(), is(true));
        assertThat(result.get().fetchDate(), is(LocalDate.of(2026, 4, 25)));
    }

    @Test
    void findLatestSnapshot_emptyTableReturnsEmpty() {
        Optional<SectorPerformanceSnapshot> result = repository.findLatestSnapshot();

        assertThat(result.isEmpty(), is(true));
    }

    @Test
    void findSnapshotsSince_returnsOnlySnapshotsInWindow() {
        repository.saveSnapshot(createSnapshot(LocalDate.of(2026, 4, 10)));
        repository.saveSnapshot(createSnapshot(LocalDate.of(2026, 4, 20)));
        repository.saveSnapshot(createSnapshot(LocalDate.of(2026, 4, 25)));

        List<SectorPerformanceSnapshot> result =
                repository.findSnapshotsSince(LocalDate.of(2026, 4, 15));

        assertThat(result, hasSize(2));
        assertThat(result.get(0).fetchDate(), is(LocalDate.of(2026, 4, 20)));
        assertThat(result.get(1).fetchDate(), is(LocalDate.of(2026, 4, 25)));
    }

    @Test
    void findSnapshotsSince_emptyTableReturnsEmptyList() {
        List<SectorPerformanceSnapshot> result =
                repository.findSnapshotsSince(LocalDate.of(2026, 1, 1));

        assertThat(result, is(empty()));
    }

    private SectorPerformanceSnapshot createSnapshot(LocalDate date) {
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
