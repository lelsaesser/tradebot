package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteTreasuryIndicatorStateRepositoryTest extends AbstractSqliteRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteTreasuryIndicatorStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteTreasuryIndicatorStateRepository(jdbcTemplate);
    }

    @Test
    void save_newSeries_insertsRecord() {
        TreasuryIndicatorState state =
                new TreasuryIndicatorState(
                        "T10Y3M",
                        "NORMAL",
                        0.65,
                        LocalDate.of(2026, 6, 23),
                        true,
                        Instant.ofEpochSecond(1_700_000_000L));

        repository.save(state);

        Optional<TreasuryIndicatorState> result = repository.findBySeriesId("T10Y3M");
        assertTrue(result.isPresent());
        assertEquals("T10Y3M", result.get().seriesId());
        assertEquals("NORMAL", result.get().lastBand());
        assertEquals(0.65, result.get().lastValue(), 0.0001);
        assertEquals(LocalDate.of(2026, 6, 23), result.get().lastObservationDate());
        assertTrue(result.get().initialized());
        assertEquals(Instant.ofEpochSecond(1_700_000_000L), result.get().updatedAt());
    }

    @Test
    void save_existingSeries_updatesRecord() {
        repository.save(
                new TreasuryIndicatorState(
                        "T10Y3M",
                        "FLAT",
                        0.10,
                        LocalDate.of(2026, 6, 22),
                        false,
                        Instant.ofEpochSecond(1_700_000_000L)));

        repository.save(
                new TreasuryIndicatorState(
                        "T10Y3M",
                        "INVERTED",
                        -0.30,
                        LocalDate.of(2026, 6, 23),
                        true,
                        Instant.ofEpochSecond(1_700_000_100L)));

        Optional<TreasuryIndicatorState> result = repository.findBySeriesId("T10Y3M");
        assertTrue(result.isPresent());
        assertEquals("INVERTED", result.get().lastBand());
        assertEquals(-0.30, result.get().lastValue(), 0.0001);
        assertEquals(LocalDate.of(2026, 6, 23), result.get().lastObservationDate());
        assertTrue(result.get().initialized());
    }

    @Test
    void findBySeriesId_nonExistent_returnsEmpty() {
        assertTrue(repository.findBySeriesId("NOT_A_REAL_SERIES").isEmpty());
    }

    @Test
    void save_multipleSeries_storesIndependently() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        repository.save(
                new TreasuryIndicatorState(
                        "T10Y3M", "NORMAL", 0.65, LocalDate.of(2026, 6, 23), true, now));
        repository.save(
                new TreasuryIndicatorState(
                        "T10Y2Y", "FLAT", 0.34, LocalDate.of(2026, 6, 23), true, now));
        repository.save(
                new TreasuryIndicatorState(
                        "DFII10", "RESTRICTIVE", 2.28, LocalDate.of(2026, 6, 22), true, now));
        repository.save(
                new TreasuryIndicatorState(
                        "THREEFYTP10", "NORMAL", 0.75, LocalDate.of(2026, 6, 18), true, now));

        assertEquals("NORMAL", repository.findBySeriesId("T10Y3M").orElseThrow().lastBand());
        assertEquals("FLAT", repository.findBySeriesId("T10Y2Y").orElseThrow().lastBand());
        assertEquals("RESTRICTIVE", repository.findBySeriesId("DFII10").orElseThrow().lastBand());
        assertEquals("NORMAL", repository.findBySeriesId("THREEFYTP10").orElseThrow().lastBand());
    }

    @Test
    void save_initializedFalse_roundtripsCorrectly() {
        repository.save(
                new TreasuryIndicatorState(
                        "T10Y3M",
                        "NORMAL",
                        0.65,
                        LocalDate.of(2026, 6, 23),
                        false,
                        Instant.ofEpochSecond(1_700_000_000L)));

        Optional<TreasuryIndicatorState> result = repository.findBySeriesId("T10Y3M");
        assertTrue(result.isPresent());
        assertFalse(result.get().initialized());
    }

    @Test
    void save_negativeValue_storesCorrectly() {
        repository.save(
                new TreasuryIndicatorState(
                        "T10Y3M",
                        "DEEPLY_INVERTED",
                        -1.50,
                        LocalDate.of(2026, 6, 23),
                        true,
                        Instant.ofEpochSecond(1_700_000_000L)));

        Optional<TreasuryIndicatorState> result = repository.findBySeriesId("T10Y3M");
        assertTrue(result.isPresent());
        assertEquals(-1.50, result.get().lastValue(), 0.0001);
        assertEquals("DEEPLY_INVERTED", result.get().lastBand());
    }
}
