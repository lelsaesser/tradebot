package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteApiMeteringRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteApiMeteringRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteApiMeteringRepository(jdbcTemplate);
    }

    @Test
    void saveAll_insertsRecords() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0, 0);
        List<ApiMeteringRecord> records =
                List.of(
                        new ApiMeteringRecord("finnhub", "2026-05", 100, now),
                        new ApiMeteringRecord("coingecko", "2026-05", 50, now));

        repository.saveAll(records);

        List<ApiMeteringRecord> result = repository.findByMonth("2026-05");
        assertEquals(2, result.size());
    }

    @Test
    void findByMonth_emptyTable_returnsEmptyList() {
        List<ApiMeteringRecord> result = repository.findByMonth("2026-05");
        assertTrue(result.isEmpty());
    }

    @Test
    void findByMonth_returnsAllProviders() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0, 0);
        List<ApiMeteringRecord> records =
                List.of(
                        new ApiMeteringRecord("finnhub", "2026-05", 100, now),
                        new ApiMeteringRecord("coingecko", "2026-05", 50, now),
                        new ApiMeteringRecord("twelvedata", "2026-05", 75, now),
                        new ApiMeteringRecord("yahoo", "2026-05", 30, now));

        repository.saveAll(records);

        List<ApiMeteringRecord> result = repository.findByMonth("2026-05");
        assertEquals(4, result.size());
        assertEquals("finnhub", result.getFirst().provider());
        assertEquals(100, result.getFirst().count());
        assertEquals("2026-05", result.getFirst().month());
    }

    @Test
    void saveAll_emptyList_doesNothing() {
        repository.saveAll(List.of());

        List<ApiMeteringRecord> result = repository.findByMonth("2026-05");
        assertTrue(result.isEmpty());
    }

    @Test
    void saveAll_differentMonths_preservesHistory() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0, 0);
        repository.saveAll(List.of(new ApiMeteringRecord("finnhub", "2026-04", 5000, now)));

        LocalDateTime later = LocalDateTime.of(2026, 5, 1, 0, 0, 0);
        repository.saveAll(List.of(new ApiMeteringRecord("finnhub", "2026-05", 0, later)));

        List<ApiMeteringRecord> aprilRecords = repository.findByMonth("2026-04");
        List<ApiMeteringRecord> mayRecords = repository.findByMonth("2026-05");
        assertEquals(1, aprilRecords.size());
        assertEquals(5000, aprilRecords.getFirst().count());
        assertEquals(1, mayRecords.size());
        assertEquals(0, mayRecords.getFirst().count());
    }

    @Test
    void saveAll_sameProviderAndMonth_updatesExistingRow() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0, 0);
        repository.saveAll(List.of(new ApiMeteringRecord("finnhub", "2026-05", 100, now)));

        LocalDateTime later = LocalDateTime.of(2026, 5, 11, 10, 10, 0);
        repository.saveAll(List.of(new ApiMeteringRecord("finnhub", "2026-05", 200, later)));

        List<ApiMeteringRecord> result = repository.findByMonth("2026-05");
        assertEquals(1, result.size());
        assertEquals(200, result.getFirst().count());
        assertEquals(later, result.getFirst().lastUpdated());
    }

    @Test
    void findByMonth_returnsOnlyMatchingMonth() {
        LocalDateTime now = LocalDateTime.of(2026, 5, 11, 10, 0, 0);
        repository.saveAll(
                List.of(
                        new ApiMeteringRecord("finnhub", "2026-04", 5000, now),
                        new ApiMeteringRecord("finnhub", "2026-05", 100, now),
                        new ApiMeteringRecord("coingecko", "2026-05", 50, now)));

        List<ApiMeteringRecord> mayRecords = repository.findByMonth("2026-05");
        assertEquals(2, mayRecords.size());

        List<ApiMeteringRecord> aprilRecords = repository.findByMonth("2026-04");
        assertEquals(1, aprilRecords.size());
        assertEquals("finnhub", aprilRecords.getFirst().provider());

        List<ApiMeteringRecord> juneRecords = repository.findByMonth("2026-06");
        assertTrue(juneRecords.isEmpty());
    }
}
