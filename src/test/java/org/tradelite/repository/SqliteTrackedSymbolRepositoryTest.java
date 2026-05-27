package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tradelite.common.AssetType;
import org.tradelite.common.SymbolRegistry.StockSymbolEntry;

class SqliteTrackedSymbolRepositoryTest extends AbstractSqliteRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteTrackedSymbolRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteTrackedSymbolRepository(jdbcTemplate);
    }

    @Test
    void save_insertsNewSymbol() {
        repository.save("AAPL", "Apple", AssetType.STOCK);

        List<StockSymbolEntry> results = repository.findAll();
        assertEquals(1, results.size());
        assertEquals("AAPL", results.getFirst().getTicker());
        assertEquals("Apple", results.getFirst().getDisplayName());
    }

    @Test
    void save_replacesExistingSymbol() {
        repository.save("AAPL", "Apple", AssetType.STOCK);
        repository.save("AAPL", "Apple Inc", AssetType.STOCK);

        List<StockSymbolEntry> results = repository.findAll();
        assertEquals(1, results.size());
        assertEquals("Apple Inc", results.getFirst().getDisplayName());
    }

    @Test
    void findAll_returnsAllSymbolsOrderedByTicker() {
        repository.save("MSFT", "Microsoft", AssetType.STOCK);
        repository.save("AAPL", "Apple", AssetType.STOCK);
        repository.save("GOOG", "Google", AssetType.STOCK);

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
    void deleteByTickerAndType_existingEntry_returnsTrue() {
        repository.save("AAPL", "Apple", AssetType.STOCK);

        boolean deleted = repository.deleteByTickerAndType("AAPL", AssetType.STOCK);

        assertTrue(deleted);
        assertTrue(repository.findAll().isEmpty());
    }

    @Test
    void deleteByTickerAndType_nonExistingEntry_returnsFalse() {
        boolean deleted = repository.deleteByTickerAndType("AAPL", AssetType.STOCK);
        assertFalse(deleted);
    }

    @Test
    void deleteByTickerAndType_wrongType_returnsFalse() {
        repository.save("AAPL", "Apple", AssetType.STOCK);

        boolean deleted = repository.deleteByTickerAndType("AAPL", AssetType.COIN);

        assertFalse(deleted);
        assertEquals(1, repository.findAll().size());
    }
}
