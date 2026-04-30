package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.tradelite.service.model.RelativeStrengthData;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqliteRsCrossoverStateRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqliteRsCrossoverStateRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqliteRsCrossoverStateRepository(jdbcTemplate);
    }

    @Test
    void save_newSymbol_insertsRecord() {
        RelativeStrengthData data = new RelativeStrengthData();
        data.setPreviousRs(1.25);
        data.setPreviousEma(1.18);
        data.setInitialized(true);

        repository.save("NVDA", data);

        Map<String, RelativeStrengthData> result = repository.findAll();
        assertTrue(result.containsKey("NVDA"));
        assertEquals(1.25, result.get("NVDA").getPreviousRs(), 0.001);
        assertEquals(1.18, result.get("NVDA").getPreviousEma(), 0.001);
        assertTrue(result.get("NVDA").isInitialized());
    }

    @Test
    void save_existingSymbol_updatesRecord() {
        RelativeStrengthData data1 = new RelativeStrengthData();
        data1.setPreviousRs(1.25);
        data1.setPreviousEma(1.18);
        data1.setInitialized(true);
        repository.save("NVDA", data1);

        RelativeStrengthData data2 = new RelativeStrengthData();
        data2.setPreviousRs(1.30);
        data2.setPreviousEma(1.22);
        data2.setInitialized(true);
        repository.save("NVDA", data2);

        Map<String, RelativeStrengthData> result = repository.findAll();
        assertEquals(1, result.size());
        assertEquals(1.30, result.get("NVDA").getPreviousRs(), 0.001);
        assertEquals(1.22, result.get("NVDA").getPreviousEma(), 0.001);
    }

    @Test
    void findAll_emptyTable_returnsEmptyMap() {
        Map<String, RelativeStrengthData> result = repository.findAll();
        assertTrue(result.isEmpty());
    }

    @Test
    void findAll_multipleSymbols_returnsAll() {
        RelativeStrengthData dataNvda = new RelativeStrengthData();
        dataNvda.setPreviousRs(1.25);
        dataNvda.setPreviousEma(1.18);
        dataNvda.setInitialized(true);

        RelativeStrengthData dataAapl = new RelativeStrengthData();
        dataAapl.setPreviousRs(0.95);
        dataAapl.setPreviousEma(1.00);
        dataAapl.setInitialized(true);

        repository.save("NVDA", dataNvda);
        repository.save("AAPL", dataAapl);

        Map<String, RelativeStrengthData> result = repository.findAll();
        assertEquals(2, result.size());
        assertEquals(1.25, result.get("NVDA").getPreviousRs(), 0.001);
        assertEquals(0.95, result.get("AAPL").getPreviousRs(), 0.001);
    }

    @Test
    void save_notInitialized_storesCorrectly() {
        RelativeStrengthData data = new RelativeStrengthData();
        data.setPreviousRs(1.10);
        data.setPreviousEma(1.05);
        data.setInitialized(false);
        repository.save("XLK", data);

        Map<String, RelativeStrengthData> result = repository.findAll();
        assertTrue(result.containsKey("XLK"));
        assertFalse(result.get("XLK").isInitialized());
    }

    @Test
    void findAll_returnsEmptyRsHistory() {
        RelativeStrengthData data = new RelativeStrengthData();
        data.setPreviousRs(1.25);
        data.setPreviousEma(1.18);
        data.setInitialized(true);
        repository.save("NVDA", data);

        Map<String, RelativeStrengthData> result = repository.findAll();
        assertTrue(result.get("NVDA").getRsHistory().isEmpty());
    }

    @Test
    void save_negativeValues_storesCorrectly() {
        RelativeStrengthData data = new RelativeStrengthData();
        data.setPreviousRs(-0.5);
        data.setPreviousEma(-0.3);
        data.setInitialized(true);
        repository.save("TEST", data);

        Map<String, RelativeStrengthData> result = repository.findAll();
        assertEquals(-0.5, result.get("TEST").getPreviousRs(), 0.001);
        assertEquals(-0.3, result.get("TEST").getPreviousEma(), 0.001);
    }
}
