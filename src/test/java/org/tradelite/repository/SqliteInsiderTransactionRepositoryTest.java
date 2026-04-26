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
import org.tradelite.repository.InsiderTransactionRepository.InsiderTransactionRow;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteInsiderTransactionRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteInsiderTransactionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteInsiderTransactionRepository(jdbcTemplate);
    }

    @Test
    void saveAll_emptyList_clearsTable() {
        repository.saveAll(List.of(new InsiderTransactionRow("AAPL", "S", 10)));

        repository.saveAll(List.of());

        List<InsiderTransactionRow> result = repository.findAll();
        assertTrue(result.isEmpty());
    }

    @Test
    void saveAll_insertsRows() {
        List<InsiderTransactionRow> rows =
                List.of(
                        new InsiderTransactionRow("AAPL", "S", 10),
                        new InsiderTransactionRow("AAPL", "P", 5),
                        new InsiderTransactionRow("GOOG", "S", 3));

        repository.saveAll(rows);

        List<InsiderTransactionRow> result = repository.findAll();
        assertEquals(3, result.size());
    }

    @Test
    void saveAll_replacesExistingData() {
        repository.saveAll(
                List.of(
                        new InsiderTransactionRow("AAPL", "S", 10),
                        new InsiderTransactionRow("GOOG", "S", 5)));

        repository.saveAll(
                List.of(
                        new InsiderTransactionRow("NVDA", "P", 20),
                        new InsiderTransactionRow("META", "S", 8)));

        List<InsiderTransactionRow> result = repository.findAll();
        assertEquals(2, result.size());

        // Old data (AAPL, GOOG) should be gone
        assertTrue(result.stream().noneMatch(r -> r.symbol().equals("AAPL")));
        assertTrue(result.stream().noneMatch(r -> r.symbol().equals("GOOG")));

        // New data should exist
        assertTrue(result.stream().anyMatch(r -> r.symbol().equals("NVDA") && r.count() == 20));
        assertTrue(result.stream().anyMatch(r -> r.symbol().equals("META") && r.count() == 8));
    }

    @Test
    void findAll_noData_returnsEmptyList() {
        List<InsiderTransactionRow> result = repository.findAll();
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_returnsAllRows() {
        repository.saveAll(
                List.of(
                        new InsiderTransactionRow("AAPL", "S", 10),
                        new InsiderTransactionRow("AAPL", "P", 5),
                        new InsiderTransactionRow("GOOG", "S", 3),
                        new InsiderTransactionRow("GOOG", "P", 1)));

        List<InsiderTransactionRow> result = repository.findAll();
        assertEquals(4, result.size());
    }

    @Test
    void saveAll_zeroCount_storesCorrectly() {
        repository.saveAll(List.of(new InsiderTransactionRow("AAPL", "S", 0)));

        List<InsiderTransactionRow> result = repository.findAll();
        assertEquals(1, result.size());
        assertEquals(0, result.getFirst().count());
    }

    @Test
    void saveAll_multipleTransactionTypes_storesCorrectly() {
        repository.saveAll(
                List.of(
                        new InsiderTransactionRow("AAPL", "S", 10),
                        new InsiderTransactionRow("AAPL", "S/V", 2),
                        new InsiderTransactionRow("AAPL", "P", 5),
                        new InsiderTransactionRow("AAPL", "P/V", 1)));

        List<InsiderTransactionRow> result = repository.findAll();
        assertEquals(4, result.size());
        assertEquals(
                10,
                result.stream()
                        .filter(r -> r.symbol().equals("AAPL") && r.transactionType().equals("S"))
                        .findFirst()
                        .orElseThrow()
                        .count());
    }
}
