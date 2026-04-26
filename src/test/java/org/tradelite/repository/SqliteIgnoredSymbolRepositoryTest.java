package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.tradelite.repository.SqliteIgnoredSymbolRepository.IgnoredSymbolRow;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteIgnoredSymbolRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteIgnoredSymbolRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteIgnoredSymbolRepository(jdbcTemplate);
    }

    @Test
    void save_newEntry_insertsRecord() {
        repository.save("AAPL", "buy_alert", 1000L, null);

        Optional<IgnoredSymbolRow> result = repository.findBySymbolAndReason("AAPL", "buy_alert");
        assertTrue(result.isPresent());
        assertEquals(1000L, result.get().ignoredAt());
        assertNull(result.get().alertThreshold());
    }

    @Test
    void save_withThreshold_insertsRecord() {
        repository.save("AAPL", "change_percent_alert", 2000L, 10);

        Optional<IgnoredSymbolRow> result =
                repository.findBySymbolAndReason("AAPL", "change_percent_alert");
        assertTrue(result.isPresent());
        assertEquals(2000L, result.get().ignoredAt());
        assertEquals(10, result.get().alertThreshold());
    }

    @Test
    void save_existingEntry_replacesRecord() {
        repository.save("AAPL", "buy_alert", 1000L, null);
        repository.save("AAPL", "buy_alert", 2000L, null);

        Optional<IgnoredSymbolRow> result = repository.findBySymbolAndReason("AAPL", "buy_alert");
        assertTrue(result.isPresent());
        assertEquals(2000L, result.get().ignoredAt());
    }

    @Test
    void save_existingThresholdEntry_replacesWithHigherThreshold() {
        repository.save("AAPL", "change_percent_alert", 1000L, 5);
        repository.save("AAPL", "change_percent_alert", 2000L, 10);

        Optional<IgnoredSymbolRow> result =
                repository.findBySymbolAndReason("AAPL", "change_percent_alert");
        assertTrue(result.isPresent());
        assertEquals(2000L, result.get().ignoredAt());
        assertEquals(10, result.get().alertThreshold());
    }

    @Test
    void save_differentReasons_storesIndependently() {
        repository.save("AAPL", "buy_alert", 1000L, null);
        repository.save("AAPL", "sell_alert", 2000L, null);

        Optional<IgnoredSymbolRow> buy = repository.findBySymbolAndReason("AAPL", "buy_alert");
        Optional<IgnoredSymbolRow> sell = repository.findBySymbolAndReason("AAPL", "sell_alert");

        assertTrue(buy.isPresent());
        assertTrue(sell.isPresent());
        assertEquals(1000L, buy.get().ignoredAt());
        assertEquals(2000L, sell.get().ignoredAt());
    }

    @Test
    void save_differentSymbols_storesIndependently() {
        repository.save("AAPL", "buy_alert", 1000L, null);
        repository.save("GOOG", "buy_alert", 2000L, null);

        Optional<IgnoredSymbolRow> aapl = repository.findBySymbolAndReason("AAPL", "buy_alert");
        Optional<IgnoredSymbolRow> goog = repository.findBySymbolAndReason("GOOG", "buy_alert");

        assertTrue(aapl.isPresent());
        assertTrue(goog.isPresent());
        assertEquals(1000L, aapl.get().ignoredAt());
        assertEquals(2000L, goog.get().ignoredAt());
    }

    @Test
    void findBySymbolAndReason_nonExistent_returnsEmpty() {
        Optional<IgnoredSymbolRow> result = repository.findBySymbolAndReason("AAPL", "buy_alert");
        assertTrue(result.isEmpty());
    }

    @Test
    void deleteExpiredEntries_removesOldEntries() {
        repository.save("AAPL", "buy_alert", 100L, null);
        repository.save("GOOG", "sell_alert", 200L, null);
        repository.save("MSFT", "buy_alert", 5000L, null);

        repository.deleteExpiredEntries(300L);

        assertTrue(repository.findBySymbolAndReason("AAPL", "buy_alert").isEmpty());
        assertTrue(repository.findBySymbolAndReason("GOOG", "sell_alert").isEmpty());
        assertTrue(repository.findBySymbolAndReason("MSFT", "buy_alert").isPresent());
    }

    @Test
    void deleteExpiredEntries_preservesFreshEntries() {
        long now = System.currentTimeMillis() / 1000;
        repository.save("AAPL", "buy_alert", now, null);
        repository.save("GOOG", "sell_alert", now, null);

        repository.deleteExpiredEntries(now - 100);

        assertTrue(repository.findBySymbolAndReason("AAPL", "buy_alert").isPresent());
        assertTrue(repository.findBySymbolAndReason("GOOG", "sell_alert").isPresent());
    }

    @Test
    void deleteExpiredEntries_noEntries_doesNothing() {
        repository.deleteExpiredEntries(1000L);
        // No exception thrown
    }
}
