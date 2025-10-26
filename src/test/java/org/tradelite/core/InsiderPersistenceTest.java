package org.tradelite.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.common.StockSymbol;
import org.tradelite.config.BeanConfig;

class InsiderPersistenceTest {

    private static final String TEST_FILE_PATH = "config/test-insider-transactions.json";

    private InsiderPersistence insiderPersistence;

    @BeforeEach
    void setUp() {
        insiderPersistence = new InsiderPersistence(new BeanConfig().objectMapper());
    }

    @Test
    void persistToFile_readFromFile_ok() {
        Map<StockSymbol, Map<String, Integer>> transactions = new EnumMap<>(StockSymbol.class);
        transactions.put(
                StockSymbol.AAPL, Map.of(InsiderTransactionCodes.SELL_HISTORIC.getCode(), 100));
        transactions.put(
                StockSymbol.GOOG, Map.of(InsiderTransactionCodes.SELL_HISTORIC.getCode(), 200));

        insiderPersistence.persistToFile(transactions, TEST_FILE_PATH);

        List<InsiderTransactionHistoric> historicData =
                insiderPersistence.readFromFile(TEST_FILE_PATH);

        assertThat(historicData.size(), is(2));

        transactions.put(
                StockSymbol.AVGO, Map.of(InsiderTransactionCodes.SELL_HISTORIC.getCode(), 300));

        insiderPersistence.persistToFile(transactions, TEST_FILE_PATH);

        List<InsiderTransactionHistoric> updatedHistoricData =
                insiderPersistence.readFromFile(TEST_FILE_PATH);

        assertThat(updatedHistoricData.size(), is(3));
    }

    @Test
    void persistToFile_exception() {
        Map<StockSymbol, Map<String, Integer>> transactions = new EnumMap<>(StockSymbol.class);
        transactions.put(
                StockSymbol.AAPL, Map.of(InsiderTransactionCodes.SELL_HISTORIC.getCode(), 100));

        String invalidFilePath = "invalid/path/insider-transactions.json";

        assertThrows(
                IllegalStateException.class,
                () -> insiderPersistence.persistToFile(transactions, invalidFilePath));
    }

    @Test
    void readFromFile_exception() {
        String invalidFilePath = "invalid/path/insider-transactions.json";

        assertThrows(
                IllegalStateException.class,
                () -> insiderPersistence.readFromFile(invalidFilePath));
    }
}
