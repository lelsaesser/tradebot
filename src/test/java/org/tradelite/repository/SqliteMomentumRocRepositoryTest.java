package org.tradelite.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.tradelite.service.model.MomentumRocData;

class SqliteMomentumRocRepositoryTest {

    private SqliteMomentumRocRepository repository;
    private String testDbPath;

    @BeforeEach
    void setUp() {
        testDbPath = "target/test-momentum-roc-" + UUID.randomUUID() + ".db";
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + testDbPath);
        repository = new SqliteMomentumRocRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        new File(testDbPath).delete();
    }

    @Test
    void save_newSymbol_insertsRecord() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(5.5);
        data.setPreviousRoc20(-2.3);
        data.setInitialized(true);

        repository.save("XLK", data);

        Optional<MomentumRocData> result = repository.findBySymbol("XLK");
        assertTrue(result.isPresent());
        assertEquals(5.5, result.get().getPreviousRoc10(), 0.001);
        assertEquals(-2.3, result.get().getPreviousRoc20(), 0.001);
        assertTrue(result.get().isInitialized());
    }

    @Test
    void save_existingSymbol_updatesRecord() {
        MomentumRocData data1 = new MomentumRocData();
        data1.setPreviousRoc10(5.5);
        data1.setPreviousRoc20(-2.3);
        data1.setInitialized(true);
        repository.save("XLK", data1);

        MomentumRocData data2 = new MomentumRocData();
        data2.setPreviousRoc10(-10.0);
        data2.setPreviousRoc20(8.0);
        data2.setInitialized(true);
        repository.save("XLK", data2);

        Optional<MomentumRocData> result = repository.findBySymbol("XLK");
        assertTrue(result.isPresent());
        assertEquals(-10.0, result.get().getPreviousRoc10(), 0.001);
        assertEquals(8.0, result.get().getPreviousRoc20(), 0.001);
    }

    @Test
    void findBySymbol_nonExistentSymbol_returnsEmpty() {
        Optional<MomentumRocData> result = repository.findBySymbol("NOTEXIST");

        assertTrue(result.isEmpty());
    }

    @Test
    void findBySymbol_existingSymbol_returnsData() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(3.3);
        data.setPreviousRoc20(6.6);
        data.setInitialized(true);
        repository.save("XLF", data);

        Optional<MomentumRocData> result = repository.findBySymbol("XLF");

        assertTrue(result.isPresent());
        assertEquals(3.3, result.get().getPreviousRoc10(), 0.001);
        assertEquals(6.6, result.get().getPreviousRoc20(), 0.001);
        assertTrue(result.get().isInitialized());
    }

    @Test
    void findBySymbol_notInitialized_returnsFalse() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(1.0);
        data.setPreviousRoc20(2.0);
        data.setInitialized(false);
        repository.save("XLE", data);

        Optional<MomentumRocData> result = repository.findBySymbol("XLE");

        assertTrue(result.isPresent());
        assertFalse(result.get().isInitialized());
    }

    @Test
    void save_multipleSymbols_storesIndependently() {
        MomentumRocData dataXlk = new MomentumRocData();
        dataXlk.setPreviousRoc10(1.0);
        dataXlk.setPreviousRoc20(2.0);
        dataXlk.setInitialized(true);

        MomentumRocData dataXlf = new MomentumRocData();
        dataXlf.setPreviousRoc10(-1.0);
        dataXlf.setPreviousRoc20(-2.0);
        dataXlf.setInitialized(false);

        repository.save("XLK", dataXlk);
        repository.save("XLF", dataXlf);

        Optional<MomentumRocData> resultXlk = repository.findBySymbol("XLK");
        Optional<MomentumRocData> resultXlf = repository.findBySymbol("XLF");

        assertTrue(resultXlk.isPresent());
        assertTrue(resultXlf.isPresent());

        assertEquals(1.0, resultXlk.get().getPreviousRoc10(), 0.001);
        assertTrue(resultXlk.get().isInitialized());

        assertEquals(-1.0, resultXlf.get().getPreviousRoc10(), 0.001);
        assertFalse(resultXlf.get().isInitialized());
    }

    @Test
    void save_zeroValues_storesCorrectly() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(0.0);
        data.setPreviousRoc20(0.0);
        data.setInitialized(true);

        repository.save("XLV", data);

        Optional<MomentumRocData> result = repository.findBySymbol("XLV");
        assertTrue(result.isPresent());
        assertEquals(0.0, result.get().getPreviousRoc10(), 0.001);
        assertEquals(0.0, result.get().getPreviousRoc20(), 0.001);
    }

    @Test
    void save_negativeValues_storesCorrectly() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(-15.5);
        data.setPreviousRoc20(-25.3);
        data.setInitialized(true);

        repository.save("XLU", data);

        Optional<MomentumRocData> result = repository.findBySymbol("XLU");
        assertTrue(result.isPresent());
        assertEquals(-15.5, result.get().getPreviousRoc10(), 0.001);
        assertEquals(-25.3, result.get().getPreviousRoc20(), 0.001);
    }

    @Test
    void save_largeValues_storesCorrectly() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(999.99);
        data.setPreviousRoc20(-888.88);
        data.setInitialized(true);

        repository.save("XLRE", data);

        Optional<MomentumRocData> result = repository.findBySymbol("XLRE");
        assertTrue(result.isPresent());
        assertEquals(999.99, result.get().getPreviousRoc10(), 0.001);
        assertEquals(-888.88, result.get().getPreviousRoc20(), 0.001);
    }

    @Test
    void findBySymbol_caseSensitive() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(5.0);
        data.setPreviousRoc20(10.0);
        data.setInitialized(true);
        repository.save("XLK", data);

        Optional<MomentumRocData> upperResult = repository.findBySymbol("XLK");
        Optional<MomentumRocData> lowerResult = repository.findBySymbol("xlk");

        assertTrue(upperResult.isPresent());
        assertTrue(lowerResult.isEmpty());
    }
}
