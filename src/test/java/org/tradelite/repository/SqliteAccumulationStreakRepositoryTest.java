package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.Month;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tradelite.core.AccumulationStreak;

class SqliteAccumulationStreakRepositoryTest extends AbstractSqliteRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteAccumulationStreakRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteAccumulationStreakRepository(jdbcTemplate);
    }

    @Test
    void save_newSymbol_insertsRecord() {
        AccumulationStreak streak =
                new AccumulationStreak("AAPL", 3, LocalDate.of(2026, Month.MAY, 15));

        repository.save(streak);

        Optional<AccumulationStreak> result = repository.findBySymbol("AAPL");
        assertTrue(result.isPresent());
        assertEquals("AAPL", result.get().symbol());
        assertEquals(3, result.get().streakDays());
        assertEquals(LocalDate.of(2026, Month.MAY, 15), result.get().lastUpdated());
    }

    @Test
    void save_existingSymbol_updatesRecord() {
        repository.save(new AccumulationStreak("AAPL", 3, LocalDate.of(2026, Month.MAY, 15)));
        repository.save(new AccumulationStreak("AAPL", 4, LocalDate.of(2026, Month.MAY, 16)));

        Optional<AccumulationStreak> result = repository.findBySymbol("AAPL");
        assertTrue(result.isPresent());
        assertEquals(4, result.get().streakDays());
        assertEquals(LocalDate.of(2026, Month.MAY, 16), result.get().lastUpdated());
    }

    @Test
    void findBySymbol_nonExistentSymbol_returnsEmpty() {
        Optional<AccumulationStreak> result = repository.findBySymbol("NOTEXIST");
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteAllExcept_keepsSpecifiedSymbols() {
        repository.save(new AccumulationStreak("AAPL", 3, LocalDate.of(2026, Month.MAY, 15)));
        repository.save(new AccumulationStreak("NVDA", 5, LocalDate.of(2026, Month.MAY, 15)));
        repository.save(new AccumulationStreak("MSFT", 2, LocalDate.of(2026, Month.MAY, 15)));

        repository.deleteAllExcept(Set.of("AAPL", "NVDA"));

        assertTrue(repository.findBySymbol("AAPL").isPresent());
        assertTrue(repository.findBySymbol("NVDA").isPresent());
        assertTrue(repository.findBySymbol("MSFT").isEmpty());
    }

    @Test
    void deleteAllExcept_emptySet_deletesAll() {
        repository.save(new AccumulationStreak("AAPL", 3, LocalDate.of(2026, Month.MAY, 15)));
        repository.save(new AccumulationStreak("NVDA", 5, LocalDate.of(2026, Month.MAY, 15)));

        repository.deleteAllExcept(Set.of());

        assertTrue(repository.findBySymbol("AAPL").isEmpty());
        assertTrue(repository.findBySymbol("NVDA").isEmpty());
    }

    @Test
    void save_multipleSymbols_storesIndependently() {
        repository.save(new AccumulationStreak("AAPL", 3, LocalDate.of(2026, Month.MAY, 15)));
        repository.save(new AccumulationStreak("NVDA", 7, LocalDate.of(2026, Month.MAY, 14)));

        Optional<AccumulationStreak> aapl = repository.findBySymbol("AAPL");
        Optional<AccumulationStreak> nvda = repository.findBySymbol("NVDA");

        assertTrue(aapl.isPresent());
        assertTrue(nvda.isPresent());
        assertEquals(3, aapl.get().streakDays());
        assertEquals(7, nvda.get().streakDays());
    }

    @Test
    void deleteBySymbol_existingSymbol_removesOnlyTargetRow() {
        repository.save(new AccumulationStreak("AAPL", 3, LocalDate.of(2026, Month.MAY, 15)));
        repository.save(new AccumulationStreak("NVDA", 4, LocalDate.of(2026, Month.MAY, 15)));

        int deleted = repository.deleteBySymbol("AAPL");

        assertEquals(1, deleted);
        assertTrue(repository.findBySymbol("AAPL").isEmpty());
        assertTrue(repository.findBySymbol("NVDA").isPresent());
    }

    @Test
    void deleteBySymbol_unknownSymbol_returnsZero() {
        int deleted = repository.deleteBySymbol("UNKNOWN");
        assertEquals(0, deleted);
    }

    @Test
    void onSymbolRemoved_delegatesToDeleteBySymbol() {
        repository.save(new AccumulationStreak("AAPL", 3, LocalDate.of(2026, Month.MAY, 15)));

        repository.onSymbolRemoved("AAPL");

        assertTrue(repository.findBySymbol("AAPL").isEmpty());
    }
}
