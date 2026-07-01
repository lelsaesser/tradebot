package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class SqliteApexPerformerRepositoryTest extends AbstractSqliteRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteApexPerformerRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteApexPerformerRepository(jdbcTemplate);
    }

    @Test
    void replaceAll_thenFindAll_roundTrips() {
        repository.replaceAll(Set.of("AAPL", "NVDA", "MSFT"));

        Set<String> result = repository.findAll();
        assertEquals(Set.of("AAPL", "NVDA", "MSFT"), result);
    }

    @Test
    void replaceAll_overwritesPreviousSet() {
        repository.replaceAll(Set.of("AAPL", "NVDA"));
        repository.replaceAll(Set.of("MSFT", "GOOG"));

        Set<String> result = repository.findAll();
        assertEquals(Set.of("MSFT", "GOOG"), result);
    }

    @Test
    void replaceAll_emptySet_clearsTable() {
        repository.replaceAll(Set.of("AAPL"));
        repository.replaceAll(Set.of());

        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void findAll_whenEmpty_returnsEmptySet() {
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void deleteBySymbol_existingSymbol_removesOnlyTargetRow() {
        repository.replaceAll(Set.of("AAPL", "NVDA"));

        int deleted = repository.deleteBySymbol("AAPL");

        assertEquals(1, deleted);
        assertEquals(Set.of("NVDA"), repository.findAll());
    }

    @Test
    void deleteBySymbol_unknownSymbol_returnsZero() {
        int deleted = repository.deleteBySymbol("UNKNOWN");
        assertEquals(0, deleted);
    }

    @Test
    void onSymbolRemoved_delegatesToDeleteBySymbol() {
        repository.replaceAll(Set.of("AAPL", "NVDA"));

        repository.onSymbolRemoved("AAPL");

        assertEquals(Set.of("NVDA"), repository.findAll());
    }
}
