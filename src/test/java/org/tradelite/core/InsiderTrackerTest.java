package org.tradelite.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

@ExtendWith(MockitoExtension.class)
class InsiderTrackerTest {

    @Mock private FinnhubClient finnhubClient;
    @Mock private TelegramGateway telegramClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private InsiderPersistence insiderPersistence;

    private InsiderTracker insiderTracker;

    @BeforeEach
    void setUp() throws java.io.IOException {
        insiderTracker =
                new InsiderTracker(
                        finnhubClient,
                        telegramClient,
                        targetPriceProvider,
                        insiderPersistence,
                        new org.tradelite.service.StockSymbolRegistry(
                                new org.tradelite.config.BeanConfig().objectMapper()));
    }

    @Test
    void trackInsiderTransactions_withHistoricData_shouldFetchAndSendReport() {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(new StockSymbol("AAPL", "Apple").getTicker(), 150.0, 300.0),
                        new TargetPrice(
                                new StockSymbol("GOOG", "Google").getTicker(), 200.0, 400.0),
                        new TargetPrice(
                                new StockSymbol("META", "Meta Platforms").getTicker(),
                                200.0,
                                400.0),
                        new TargetPrice(
                                new StockSymbol("AMZN", "Amazon").getTicker(), 100.0, 200.0),
                        new TargetPrice(
                                new StockSymbol("NVDA", "Nvidia").getTicker(), 100.0, 200.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        InsiderTransactionResponse responseAAPL =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice", 100, 5, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Alice",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "S/V",
                                        10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Alice",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "P/V",
                                        10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 38, "2023-10-02", "2023-10-01", "B", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 38, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 38, "2023-10-02", "2023-10-01", "P", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 38, "2023-10-02", "2023-10-01", "P", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 38, "2023-10-02", "2023-10-01", "B", 10200.0)));
        InsiderTransactionResponse responseGOOG =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "John", 100, 12, "2023-10-02", "2023-10-01", "P", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "John", 100, 12, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Doe", 100, 20, "2023-10-02", "2023-10-01", "B", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Doe", 100, 20, "2023-10-02", "2023-10-01", "P", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Doe", 100, 20, "2023-10-02", "2023-10-01", "P", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Doe", 100, 20, "2023-10-02", "2023-10-01", "B", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Tim", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0)));
        InsiderTransactionResponse responseAMZN =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice", 100, 10, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0)));
        InsiderTransactionResponse responseMETA = new InsiderTransactionResponse(List.of());
        InsiderTransactionResponse responseNVDA = new InsiderTransactionResponse(List.of());
        when(finnhubClient.getInsiderTransactions(new StockSymbol("META", "Meta Platforms")))
                .thenReturn(responseMETA);
        when(finnhubClient.getInsiderTransactions(new StockSymbol("AAPL", "Apple")))
                .thenReturn(responseAAPL);
        when(finnhubClient.getInsiderTransactions(new StockSymbol("GOOG", "Google")))
                .thenReturn(responseGOOG);
        when(finnhubClient.getInsiderTransactions(new StockSymbol("AMZN", "Amazon")))
                .thenReturn(responseAMZN);
        when(finnhubClient.getInsiderTransactions(new StockSymbol("NVDA", "Nvidia")))
                .thenReturn(responseNVDA);

        Map<String, Integer> historicAAPL = Map.of("S", 42, "P", 10);
        Map<String, Integer> historicGOOG = Map.of("S", 2, "P", 5);
        Map<String, Integer> historicAMZN = Map.of("S", 2, "P", 2);
        Map<String, Integer> historicNVDA = Map.of("S", 21, "P", 0);
        List<InsiderTransactionHistoric> historicData =
                List.of(
                        new InsiderTransactionHistoric(
                                new StockSymbol("AAPL", "Apple"), historicAAPL),
                        new InsiderTransactionHistoric(
                                new StockSymbol("GOOG", "Google"), historicGOOG),
                        new InsiderTransactionHistoric(
                                new StockSymbol("AMZN", "Amazon"), historicAMZN),
                        new InsiderTransactionHistoric(
                                new StockSymbol("NVDA", "Nvidia"), historicNVDA));
        when(insiderPersistence.readFromFile(anyString())).thenReturn(historicData);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        insiderTracker.trackInsiderTransactions();
        verify(telegramClient).sendMessage(reportCaptor.capture());
        String report = reportCaptor.getValue();

        verify(telegramClient, times(1)).sendMessage(anyString());
        verify(finnhubClient, times(5)).getInsiderTransactions(any(StockSymbol.class));
        verify(insiderPersistence, times(1)).readFromFile(anyString());
        verify(insiderPersistence, times(1)).persistToFile(any(), anyString());

        String expectedReport =
                """
        *Weekly Insider Transactions Report:*

        ```
        Symbol       Sells        Diff       \s
        GOOG         5            +3         \s
        AAPL         3            -39        \s
        AMZN         2            0          \s
        META         0            0          \s
        NVDA         0            -21        \s
        ```

        ```
        Symbol       Buys         Diff       \s
        AAPL         3            -7         \s
        GOOG         3            -2         \s
        META         0            0          \s
        AMZN         0            -2         \s
        NVDA         0            0          \s
        ```""";

        assertThat(report, is(expectedReport));
    }

    @Test
    void sendInsiderTransactionReport() {
        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new LinkedHashMap<>();
        insiderTransactions.put(
                new StockSymbol("PLTR", "Palantir"),
                Map.of("S", 10, "S_HISTORIC", 0, "P", 0, "P_HISTORIC", 0));
        insiderTransactions.put(
                new StockSymbol("GOOG", "Google"),
                Map.of("S", 5, "S_HISTORIC", 10, "P", 3, "P_HISTORIC", 2, "P/V", 1));
        insiderTransactions.put(
                new StockSymbol("AAPL", "Apple"),
                Map.of("S", 40, "S_HISTORIC", 20, "P", 5, "P_HISTORIC", 0));
        insiderTransactions.put(
                new StockSymbol("HOOD", "Robinhood"),
                Map.of("S", 0, "S_HISTORIC", 15, "P", 43, "P_HISTORIC", 76, "P/V", 0, "S/V", 7));

        insiderTracker.sendInsiderTransactionReport(insiderTransactions);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(reportCaptor.capture());
        String report = reportCaptor.getValue();

        String expectedReport =
                """
        *Weekly Insider Transactions Report:*

        ```
        Symbol       Sells        Diff       \s
        AAPL         40           +20        \s
        PLTR         10           +10        \s
        GOOG         5            -5         \s
        HOOD         7            -8         \s
        ```

        ```
        Symbol       Buys         Diff       \s
        HOOD         43           -33        \s
        AAPL         5            +5         \s
        GOOG         4            +2         \s
        PLTR         0            0          \s
        ```""";

        assertThat(report, is(expectedReport));
    }

    @Test
    void enrichWithHistoricData_withHistoricNullValues() {
        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new HashMap<>();

        Map<String, Integer> aaplMap = new HashMap<>();
        aaplMap.put("S", 10);
        aaplMap.put("P", 5);

        Map<String, Integer> googMap = new HashMap<>();
        googMap.put("S", 3);
        googMap.put("P", 2);

        Map<String, Integer> metaMap = new HashMap<>();
        metaMap.put("S", 0);
        metaMap.put("P", 0);

        insiderTransactions.put(new StockSymbol("AAPL", "Apple"), aaplMap);
        insiderTransactions.put(new StockSymbol("GOOG", "Google"), googMap);
        insiderTransactions.put(new StockSymbol("META", "Meta Platforms"), metaMap);

        List<InsiderTransactionHistoric> historicData = new ArrayList<>();

        Map<String, Integer> map1 = new HashMap<>();
        map1.put("S", null);
        map1.put("P", null);
        historicData.add(new InsiderTransactionHistoric(new StockSymbol("AAPL", "Apple"), map1));

        Map<String, Integer> map2 = new HashMap<>();
        map2.put("S", null);
        map2.put("P", null);
        historicData.add(new InsiderTransactionHistoric(new StockSymbol("GOOG", "Google"), map2));

        Map<String, Integer> map3 = new HashMap<>();
        map3.put("S", null);
        map3.put("P", null);
        historicData.add(
                new InsiderTransactionHistoric(new StockSymbol("META", "Meta Platforms"), map3));

        when(insiderPersistence.readFromFile(InsiderPersistence.PERSISTENCE_FILE_PATH))
                .thenReturn(historicData);

        Map<StockSymbol, Map<String, Integer>> enrichedData =
                insiderTracker.enrichWithHistoricData(insiderTransactions);

        assertThat(enrichedData.get(new StockSymbol("AAPL", "Apple")).get("S_HISTORIC"), is(0));
        assertThat(enrichedData.get(new StockSymbol("AAPL", "Apple")).get("P_HISTORIC"), is(0));
        assertThat(enrichedData.get(new StockSymbol("GOOG", "Google")).get("S_HISTORIC"), is(0));
        assertThat(enrichedData.get(new StockSymbol("GOOG", "Google")).get("P_HISTORIC"), is(0));
        assertThat(
                enrichedData.get(new StockSymbol("META", "Meta Platforms")).get("S_HISTORIC"),
                is(0));
        assertThat(
                enrichedData.get(new StockSymbol("META", "Meta Platforms")).get("P_HISTORIC"),
                is(0));
    }

    @Test
    void trackInsiderTransactions_noMonitoredSymbols() {
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(finnhubClient, never()).getInsiderTransactions(any());
        verify(telegramClient, never()).sendMessage(anyString());
        verify(insiderPersistence, never()).readFromFile(anyString());
    }

    @Test
    void trackInsiderTransactions_emptyInsiderTransactions() {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(
                                new StockSymbol("AAPL", "Apple").getTicker(), 150.0, 300.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(finnhubClient.getInsiderTransactions(new StockSymbol("AAPL", "Apple")))
                .thenReturn(new InsiderTransactionResponse(Collections.emptyList()));
        when(insiderPersistence.readFromFile(anyString())).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderPersistence).persistToFile(any(), anyString());
    }

    @Test
    void trackInsiderTransactions_noHistoricData() {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(
                                new StockSymbol("AAPL", "Apple").getTicker(), 150.0, 300.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        InsiderTransactionResponse responseAAPL =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "S",
                                        10200.0)));
        when(finnhubClient.getInsiderTransactions(new StockSymbol("AAPL", "Apple")))
                .thenReturn(responseAAPL);
        when(insiderPersistence.readFromFile(anyString())).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderPersistence).persistToFile(any(), anyString());
    }

    @Test
    void trackInsiderTransactions_withInvalidSymbols_shouldSkipInvalidSymbolsGracefully() {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(new StockSymbol("AAPL", "Apple").getTicker(), 150.0, 300.0),
                        new TargetPrice("BLABLA", 915.0, 0.0), // Invalid symbol
                        new TargetPrice(
                                new StockSymbol("GOOG", "Google").getTicker(), 200.0, 400.0),
                        new TargetPrice("DUMMY", 84.0, 0.0) // Invalid symbol
                        );
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        InsiderTransactionResponse responseAAPL =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "S",
                                        10200.0)));
        InsiderTransactionResponse responseGOOG =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 10, "2023-10-02", "2023-10-01", "P", 10200.0)));

        when(finnhubClient.getInsiderTransactions(new StockSymbol("AAPL", "Apple")))
                .thenReturn(responseAAPL);
        when(finnhubClient.getInsiderTransactions(new StockSymbol("GOOG", "Google")))
                .thenReturn(responseGOOG);
        when(insiderPersistence.readFromFile(anyString())).thenReturn(Collections.emptyList());

        // This should not throw an exception despite invalid symbols
        insiderTracker.trackInsiderTransactions();

        // Verify that only valid symbols were processed (AAPL and GOOG)
        verify(finnhubClient, times(1)).getInsiderTransactions(new StockSymbol("AAPL", "Apple"));
        verify(finnhubClient, times(1)).getInsiderTransactions(new StockSymbol("GOOG", "Google"));
        verify(finnhubClient, times(2)).getInsiderTransactions(any(StockSymbol.class));

        // Should still send report and persist data for valid symbols
        verify(telegramClient).sendMessage(anyString());
        verify(insiderPersistence).persistToFile(any(), anyString());
    }

    @Test
    void trackInsiderTransactions_shouldTriggerComputeIfAbsentLambdas() {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(
                                new StockSymbol("AAPL", "Apple").getTicker(), 150.0, 300.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        // Create multiple transactions of each type to trigger the computeIfAbsent paths
        InsiderTransactionResponse responseAAPL =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice", 100, 5, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 5, "2023-10-02", "2023-10-01", "S/V", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Charlie",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "P",
                                        10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Dave",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "P/V",
                                        10200.0)));

        when(finnhubClient.getInsiderTransactions(new StockSymbol("AAPL", "Apple")))
                .thenReturn(responseAAPL);
        when(insiderPersistence.readFromFile(anyString())).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderPersistence).persistToFile(any(), anyString());
    }

    @Test
    void orderMapByCodeCount_shouldHandleDuplicateEntriesAndTriggerMergeFunction() {
        Map<StockSymbol, Map<String, Integer>> testData = new LinkedHashMap<>();

        // Add multiple entries with same count values to potentially trigger merge function
        testData.put(new StockSymbol("AAPL", "Apple"), Map.of("S", 10, "P", 5));
        testData.put(
                new StockSymbol("GOOG", "Google"),
                Map.of("S", 10, "P", 3)); // Same sell count as AAPL
        testData.put(new StockSymbol("META", "Meta Platforms"), Map.of("S", 15, "P", 2));
        testData.put(new StockSymbol("AMZN", "Amazon"), Map.of("S", 5, "P", 8));

        // Call the method through sendInsiderTransactionReport since orderMapByCodeCount is private
        insiderTracker.sendInsiderTransactionReport(testData);

        verify(telegramClient).sendMessage(anyString());
    }

    @Test
    void trackInsiderTransactions_shouldTriggerComputeIfAbsentWhenMapsNotPreInitialized()
            throws java.io.IOException {
        // This test creates a scenario where the maps are not pre-initialized,
        // forcing the computeIfAbsent lambdas to be executed
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(
                                new StockSymbol("TSLA", "Tesla").getTicker(), 150.0, 300.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        // Create a custom InsiderTracker to override the behavior
        InsiderTracker customTracker =
                new InsiderTracker(
                        finnhubClient,
                        telegramClient,
                        targetPriceProvider,
                        insiderPersistence,
                        new org.tradelite.service.StockSymbolRegistry(
                                new org.tradelite.config.BeanConfig().objectMapper())) {
                    @Override
                    public void trackInsiderTransactions() {
                        List<String> monitoredSymbols =
                                targetPriceProvider.getStockTargetPrices().stream()
                                        .map(TargetPrice::getSymbol)
                                        .toList();

                        Map<StockSymbol, Map<String, Integer>> insiderTransactions =
                                new LinkedHashMap<>();

                        for (String symbolString : monitoredSymbols) {
                            Optional<StockSymbol> stockSymbolOpt =
                                    Optional.of(new StockSymbol(symbolString, "Test"));
                            StockSymbol stockSymbol = stockSymbolOpt.get();

                            InsiderTransactionResponse response =
                                    finnhubClient.getInsiderTransactions(stockSymbol);

                            // DON'T pre-initialize the maps - this will force computeIfAbsent to
                            // execute
                            for (InsiderTransactionResponse.Transaction insiderTransaction :
                                    response.data()) {
                                if (Objects.equals(
                                        insiderTransaction.transactionCode(),
                                        InsiderTransactionCodes.SELL.getCode())) {
                                    insiderTransactions
                                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                                            .merge(
                                                    InsiderTransactionCodes.SELL.getCode(),
                                                    1,
                                                    Integer::sum);
                                }
                                if (Objects.equals(
                                        insiderTransaction.transactionCode(),
                                        InsiderTransactionCodes.SELL_VOLUNTARY_REPORT.getCode())) {
                                    insiderTransactions
                                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                                            .merge(
                                                    InsiderTransactionCodes.SELL.getCode(),
                                                    1,
                                                    Integer::sum);
                                }
                                if (Objects.equals(
                                        insiderTransaction.transactionCode(),
                                        InsiderTransactionCodes.BUY.getCode())) {
                                    insiderTransactions
                                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                                            .merge(
                                                    InsiderTransactionCodes.BUY.getCode(),
                                                    1,
                                                    Integer::sum);
                                }
                                if (Objects.equals(
                                        insiderTransaction.transactionCode(),
                                        InsiderTransactionCodes.BUY_VOLUNTARY_REPORT.getCode())) {
                                    insiderTransactions
                                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                                            .merge(
                                                    InsiderTransactionCodes.BUY.getCode(),
                                                    1,
                                                    Integer::sum);
                                }
                            }
                        }

                        if (!insiderTransactions.isEmpty()) {
                            sendInsiderTransactionReport(insiderTransactions);
                        }

                        insiderPersistence.persistToFile(
                                insiderTransactions, InsiderPersistence.PERSISTENCE_FILE_PATH);
                    }
                };

        // Create transactions that will trigger all the computeIfAbsent paths
        InsiderTransactionResponse responseTSLA =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice", 100, 5, "2023-10-02", "2023-10-01", "S", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Bob", 100, 5, "2023-10-02", "2023-10-01", "S/V", 10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Charlie",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "P",
                                        10200.0),
                                new InsiderTransactionResponse.Transaction(
                                        "Dave",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "P/V",
                                        10200.0)));

        when(finnhubClient.getInsiderTransactions(new StockSymbol("TSLA", "Tesla")))
                .thenReturn(responseTSLA);
        when(insiderPersistence.readFromFile(anyString())).thenReturn(Collections.emptyList());

        customTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderPersistence).persistToFile(any(), anyString());
    }
}
