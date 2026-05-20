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
}
