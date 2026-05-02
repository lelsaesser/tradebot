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
import org.tradelite.common.AssetType;
import org.tradelite.common.SymbolRegistry.StockSymbolEntry;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteTrackedSymbolRepositoryTest {

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
    void findByAssetType_returnsOnlyMatchingType() {
        repository.save("AAPL", "Apple", AssetType.STOCK);
        repository.save("BITCOIN", "Bitcoin", AssetType.COIN);

        List<StockSymbolEntry> stocks = repository.findByAssetType(AssetType.STOCK);
        List<StockSymbolEntry> coins = repository.findByAssetType(AssetType.COIN);

        assertEquals(1, stocks.size());
        assertEquals("AAPL", stocks.getFirst().getTicker());
        assertEquals(1, coins.size());
        assertEquals("BITCOIN", coins.getFirst().getTicker());
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

    @Test
    void count_returnsTotal() {
        assertEquals(0, repository.count());

        repository.save("AAPL", "Apple", AssetType.STOCK);
        repository.save("BITCOIN", "Bitcoin", AssetType.COIN);

        assertEquals(2, repository.count());
    }
}
