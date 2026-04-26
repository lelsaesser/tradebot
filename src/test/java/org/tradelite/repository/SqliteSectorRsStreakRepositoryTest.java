package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.tradelite.core.SectorRsStreak;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteSectorRsStreakRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteSectorRsStreakRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteSectorRsStreakRepository(jdbcTemplate);
    }

    @Test
    void save_newSymbol_insertsRecord() {
        SectorRsStreak streak = new SectorRsStreak("XLK", 3, true, LocalDate.of(2026, 4, 25));

        repository.save(streak);

        Optional<SectorRsStreak> result = repository.findBySymbol("XLK");
        assertTrue(result.isPresent());
        assertEquals("XLK", result.get().symbol());
        assertEquals(3, result.get().streakDays());
        assertTrue(result.get().isOutperforming());
        assertEquals(LocalDate.of(2026, 4, 25), result.get().lastUpdated());
    }

    @Test
    void save_existingSymbol_updatesRecord() {
        repository.save(new SectorRsStreak("XLK", 3, true, LocalDate.of(2026, 4, 25)));
        repository.save(new SectorRsStreak("XLK", 1, false, LocalDate.of(2026, 4, 26)));

        Optional<SectorRsStreak> result = repository.findBySymbol("XLK");
        assertTrue(result.isPresent());
        assertEquals(1, result.get().streakDays());
        assertFalse(result.get().isOutperforming());
        assertEquals(LocalDate.of(2026, 4, 26), result.get().lastUpdated());
    }

    @Test
    void findBySymbol_nonExistentSymbol_returnsEmpty() {
        Optional<SectorRsStreak> result = repository.findBySymbol("NOTEXIST");
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_noData_returnsEmptyMap() {
        Map<String, SectorRsStreak> result = repository.findAll();
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_withData_returnsAllStreaks() {
        repository.save(new SectorRsStreak("XLK", 5, true, LocalDate.of(2026, 4, 25)));
        repository.save(new SectorRsStreak("XLU", 2, false, LocalDate.of(2026, 4, 25)));
        repository.save(new SectorRsStreak("XLF", 1, true, LocalDate.of(2026, 4, 25)));

        Map<String, SectorRsStreak> result = repository.findAll();
        assertEquals(3, result.size());
        assertTrue(result.containsKey("XLK"));
        assertTrue(result.containsKey("XLU"));
        assertTrue(result.containsKey("XLF"));
        assertEquals(5, result.get("XLK").streakDays());
        assertFalse(result.get("XLU").isOutperforming());
    }

    @Test
    void save_multipleSymbols_storesIndependently() {
        repository.save(new SectorRsStreak("XLK", 10, true, LocalDate.of(2026, 4, 25)));
        repository.save(new SectorRsStreak("XLU", 3, false, LocalDate.of(2026, 4, 25)));

        Optional<SectorRsStreak> xlk = repository.findBySymbol("XLK");
        Optional<SectorRsStreak> xlu = repository.findBySymbol("XLU");

        assertTrue(xlk.isPresent());
        assertTrue(xlu.isPresent());
        assertEquals(10, xlk.get().streakDays());
        assertTrue(xlk.get().isOutperforming());
        assertEquals(3, xlu.get().streakDays());
        assertFalse(xlu.get().isOutperforming());
    }
}
