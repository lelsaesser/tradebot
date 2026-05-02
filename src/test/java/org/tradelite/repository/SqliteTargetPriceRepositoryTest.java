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
import org.tradelite.common.TargetPrice;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteTargetPriceRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteTargetPriceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteTargetPriceRepository(jdbcTemplate);
    }

    @Test
    void save_insertsNewTargetPrice() {
        TargetPrice tp = new TargetPrice("AAPL", 150.0, 200.0);

        repository.save(tp, AssetType.STOCK);

        List<TargetPrice> results = repository.findByAssetType(AssetType.STOCK);
        assertEquals(1, results.size());
        assertEquals("AAPL", results.getFirst().getSymbol());
        assertEquals(150.0, results.getFirst().getBuyTarget());
        assertEquals(200.0, results.getFirst().getSellTarget());
    }

    @Test
    void save_replacesExistingTargetPrice() {
        repository.save(new TargetPrice("AAPL", 150.0, 200.0), AssetType.STOCK);
        repository.save(new TargetPrice("AAPL", 160.0, 210.0), AssetType.STOCK);

        List<TargetPrice> results = repository.findByAssetType(AssetType.STOCK);
        assertEquals(1, results.size());
        assertEquals(160.0, results.getFirst().getBuyTarget());
        assertEquals(210.0, results.getFirst().getSellTarget());
    }

    @Test
    void findByAssetType_returnsOnlyMatchingType() {
        repository.save(new TargetPrice("AAPL", 150.0, 200.0), AssetType.STOCK);
        repository.save(new TargetPrice("MSFT", 300.0, 400.0), AssetType.STOCK);
        repository.save(new TargetPrice("BITCOIN", 100000.0, 0.0), AssetType.COIN);

        List<TargetPrice> stocks = repository.findByAssetType(AssetType.STOCK);
        List<TargetPrice> coins = repository.findByAssetType(AssetType.COIN);

        assertEquals(2, stocks.size());
        assertEquals(1, coins.size());
        assertEquals("BITCOIN", coins.getFirst().getSymbol());
    }

    @Test
    void findByAssetType_emptyTable_returnsEmptyList() {
        List<TargetPrice> results = repository.findByAssetType(AssetType.STOCK);
        assertTrue(results.isEmpty());
    }

    @Test
    void deleteBySymbolAndType_existingEntry_returnsTrue() {
        repository.save(new TargetPrice("AAPL", 150.0, 200.0), AssetType.STOCK);

        boolean deleted = repository.deleteBySymbolAndType("AAPL", AssetType.STOCK);

        assertTrue(deleted);
        assertTrue(repository.findByAssetType(AssetType.STOCK).isEmpty());
    }

    @Test
    void deleteBySymbolAndType_nonExistingEntry_returnsFalse() {
        boolean deleted = repository.deleteBySymbolAndType("AAPL", AssetType.STOCK);
        assertFalse(deleted);
    }

    @Test
    void deleteBySymbolAndType_wrongType_returnsFalse() {
        repository.save(new TargetPrice("AAPL", 150.0, 200.0), AssetType.STOCK);

        boolean deleted = repository.deleteBySymbolAndType("AAPL", AssetType.COIN);

        assertFalse(deleted);
        assertEquals(1, repository.findByAssetType(AssetType.STOCK).size());
    }

    @Test
    void count_returnsTotal() {
        assertEquals(0, repository.count());

        repository.save(new TargetPrice("AAPL", 150.0, 200.0), AssetType.STOCK);
        repository.save(new TargetPrice("BITCOIN", 100000.0, 0.0), AssetType.COIN);

        assertEquals(2, repository.count());
    }
}
