package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.tradelite.common.SymbolRegistry.StockSymbolEntry;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteStockSymbolRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteStockSymbolRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteStockSymbolRepository(jdbcTemplate);
    }

    @Test
    void save_insertsNewSymbol() {
        repository.save("AAPL", "Apple");

        List<StockSymbolEntry> results = repository.findAll();
        assertEquals(1, results.size());
        assertEquals("AAPL", results.getFirst().getTicker());
        assertEquals("Apple", results.getFirst().getDisplayName());
    }

    @Test
    void save_replacesExistingSymbol() {
        repository.save("AAPL", "Apple");
        repository.save("AAPL", "Apple Inc");

        List<StockSymbolEntry> results = repository.findAll();
        assertEquals(1, results.size());
        assertEquals("Apple Inc", results.getFirst().getDisplayName());
    }

    @Test
    void findAll_returnsAllSymbolsOrderedByTicker() {
        repository.save("MSFT", "Microsoft");
        repository.save("AAPL", "Apple");
        repository.save("GOOG", "Google");

        List<StockSymbolEntry> results = repository.findAll();
        assertEquals(3, results.size());
        assertEquals("AAPL", results.get(0).getTicker());
        assertEquals("GOOG", results.get(1).getTicker());
        assertEquals("MSFT", results.get(2).getTicker());
    }

    @Test
    void findAll_emptyTable_returnsEmptyList() {
        List<StockSymbolEntry> results = repository.findAll();
        assertTrue(results.isEmpty());
    }

    @Test
    void deleteByTicker_existingEntry_returnsTrue() {
        repository.save("AAPL", "Apple");

        boolean deleted = repository.deleteByTicker("AAPL");

        assertTrue(deleted);
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void deleteByTicker_nonExistingEntry_returnsFalse() {
        boolean deleted = repository.deleteByTicker("AAPL");
        assertFalse(deleted);
    }

    @Test
    void count_returnsTotal() {
        assertEquals(0, repository.count());

        repository.save("AAPL", "Apple");
        repository.save("MSFT", "Microsoft");

        assertEquals(2, repository.count());
    }
}
