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
import org.tradelite.repository.InsiderTransactionRepository;
import org.tradelite.repository.InsiderTransactionRepository.InsiderTransactionRow;

@ExtendWith(MockitoExtension.class)
class InsiderTrackerTest {

    @Mock private FinnhubClient finnhubClient;
    @Mock private TelegramGateway telegramClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private InsiderTransactionRepository insiderTransactionRepository;

    private InsiderTracker insiderTracker;

    @BeforeEach
    void setUp() throws java.io.IOException {
        insiderTracker =
                new InsiderTracker(
                        finnhubClient,
                        telegramClient,
                        targetPriceProvider,
                        insiderTransactionRepository,
                        new org.tradelite.common.SymbolRegistry(
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

        List<InsiderTransactionRow> historicRows =
                List.of(
                        new InsiderTransactionRow("AAPL", "S", 42),
                        new InsiderTransactionRow("AAPL", "P", 10),
                        new InsiderTransactionRow("GOOG", "S", 2),
                        new InsiderTransactionRow("GOOG", "P", 5),
                        new InsiderTransactionRow("AMZN", "S", 2),
                        new InsiderTransactionRow("AMZN", "P", 2),
                        new InsiderTransactionRow("NVDA", "S", 21),
                        new InsiderTransactionRow("NVDA", "P", 0));
        when(insiderTransactionRepository.findAll()).thenReturn(historicRows);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        insiderTracker.trackInsiderTransactions();
        verify(telegramClient).sendMessage(reportCaptor.capture());
        String report = reportCaptor.getValue();

        verify(telegramClient, times(1)).sendMessage(anyString());
        verify(finnhubClient, times(5)).getInsiderTransactions(any(StockSymbol.class));
        verify(insiderTransactionRepository, times(1)).findAll();
        verify(insiderTransactionRepository, times(1)).saveAll(any());

        String expectedReport =
                """
        *Weekly Insider Transactions Report:*

        ```
        Symbol       Sells        Diff       \s
        GOOG         5            +3         \s
        AAPL         3            -39        \s
        AMZN         2            0          \s
        NVDA         0            -21        \s
        ```

        ```
        Symbol       Buys         Diff       \s
        AAPL         3            -7         \s
        GOOG         3            -2         \s
        AMZN         0            -2         \s
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
        ```""";

        assertThat(report, is(expectedReport));
    }

    @Test
    void enrichWithHistoricData_withHistoricSymbolNotInCurrentTransactions_shouldSkipGracefully() {
        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new HashMap<>();

        Map<String, Integer> aaplMap = new HashMap<>();
        aaplMap.put("S", 10);
        aaplMap.put("P", 5);

        insiderTransactions.put(new StockSymbol("AAPL", "Apple"), aaplMap);

        List<InsiderTransactionRow> historicRows =
                List.of(
                        new InsiderTransactionRow("AAPL", "S", 5),
                        new InsiderTransactionRow("AAPL", "P", 2),
                        new InsiderTransactionRow("MSFT", "S", 8),
                        new InsiderTransactionRow("MSFT", "P", 3));
        when(insiderTransactionRepository.findAll()).thenReturn(historicRows);

        Map<StockSymbol, Map<String, Integer>> enrichedData =
                insiderTracker.enrichWithHistoricData(insiderTransactions);

        assertThat(enrichedData.get(new StockSymbol("AAPL", "Apple")).get("S_HISTORIC"), is(5));
        assertThat(enrichedData.get(new StockSymbol("AAPL", "Apple")).get("P_HISTORIC"), is(2));

        assertThat(enrichedData.containsKey(new StockSymbol("MSFT", "Microsoft")), is(false));
    }

    @Test
    void enrichWithHistoricData_withNoHistoricData() {
        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new HashMap<>();

        Map<String, Integer> aaplMap = new HashMap<>();
        aaplMap.put("S", 10);
        aaplMap.put("P", 5);

        insiderTransactions.put(new StockSymbol("AAPL", "Apple"), aaplMap);

        when(insiderTransactionRepository.findAll()).thenReturn(List.of());

        Map<StockSymbol, Map<String, Integer>> enrichedData =
                insiderTracker.enrichWithHistoricData(insiderTransactions);

        assertThat(
                enrichedData.get(new StockSymbol("AAPL", "Apple")).containsKey("S_HISTORIC"),
                is(false));
    }

    @Test
    void trackInsiderTransactions_noMonitoredSymbols() {
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(finnhubClient, never()).getInsiderTransactions(any());
        verify(telegramClient, never()).sendMessage(anyString());
        verify(insiderTransactionRepository, never()).findAll();
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
        when(insiderTransactionRepository.findAll()).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderTransactionRepository).saveAll(any());
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
        when(insiderTransactionRepository.findAll()).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderTransactionRepository).saveAll(any());
    }

    @Test
    void trackInsiderTransactions_withInvalidSymbols_shouldSkipInvalidSymbolsGracefully() {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(new StockSymbol("AAPL", "Apple").getTicker(), 150.0, 300.0),
                        new TargetPrice("BLABLA", 915.0, 0.0),
                        new TargetPrice(
                                new StockSymbol("GOOG", "Google").getTicker(), 200.0, 400.0),
                        new TargetPrice("DUMMY", 84.0, 0.0));
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
        when(insiderTransactionRepository.findAll()).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(finnhubClient, times(1)).getInsiderTransactions(new StockSymbol("AAPL", "Apple"));
        verify(finnhubClient, times(1)).getInsiderTransactions(new StockSymbol("GOOG", "Google"));
        verify(finnhubClient, times(2)).getInsiderTransactions(any(StockSymbol.class));

        verify(telegramClient).sendMessage(anyString());
        verify(insiderTransactionRepository).saveAll(any());
    }

    @Test
    void trackInsiderTransactions_shouldTriggerComputeIfAbsentLambdas() {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(
                                new StockSymbol("AAPL", "Apple").getTicker(), 150.0, 300.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

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
        when(insiderTransactionRepository.findAll()).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderTransactionRepository).saveAll(any());
    }

    @Test
    void orderMapByCodeCount_shouldHandleDuplicateEntriesAndTriggerMergeFunction() {
        Map<StockSymbol, Map<String, Integer>> testData = new LinkedHashMap<>();

        testData.put(new StockSymbol("AAPL", "Apple"), Map.of("S", 10, "P", 5));
        testData.put(new StockSymbol("GOOG", "Google"), Map.of("S", 10, "P", 3));
        testData.put(new StockSymbol("META", "Meta Platforms"), Map.of("S", 15, "P", 2));
        testData.put(new StockSymbol("AMZN", "Amazon"), Map.of("S", 5, "P", 8));

        insiderTracker.sendInsiderTransactionReport(testData);

        verify(telegramClient).sendMessage(anyString());
    }
}
