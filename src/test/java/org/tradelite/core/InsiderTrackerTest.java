package org.tradelite.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InsiderTrackerTest {

    @Mock
    private FinnhubClient finnhubClient;
    @Mock
    private TelegramClient telegramClient;
    @Mock
    private TargetPriceProvider targetPriceProvider;
    @Mock
    private InsiderPersistence insiderPersistence;

    private InsiderTracker insiderTracker;

    @BeforeEach
    void setUp() {
        insiderTracker = new InsiderTracker(finnhubClient, telegramClient, targetPriceProvider, insiderPersistence);
    }

    @Test
    void trackInsiderTransactions_withHistoricData_shouldFetchAndSendReport() {
        List<TargetPrice> targetPrices = List.of(
            new TargetPrice(StockSymbol.AAPL.getTicker(), 150.0, 300.0),
            new TargetPrice(StockSymbol.GOOG.getTicker(), 200.0, 400.0),
            new TargetPrice(StockSymbol.META.getTicker(), 200.0, 400.0),
            new TargetPrice(StockSymbol.AMZN.getTicker(), 100.0, 200.0),
            new TargetPrice(StockSymbol.NVDA.getTicker(),  100.0, 200.0)
        );
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        InsiderTransactionResponse responseAAPL = new InsiderTransactionResponse(List.of(
            new InsiderTransactionResponse.Transaction("Alice", 100, 5, "2023-10-02", "2023-10-01", "S", 10200.0),
            new InsiderTransactionResponse.Transaction("Alice", 100, 5, "2023-10-02", "2023-10-01", "S/V", 10200.0),
            new InsiderTransactionResponse.Transaction("Alice", 100, 5, "2023-10-02", "2023-10-01", "P/V", 10200.0),
            new InsiderTransactionResponse.Transaction("Bob", 100, 38, "2023-10-02", "2023-10-01", "B", 10200.0),
            new InsiderTransactionResponse.Transaction("Bob", 100, 38, "2023-10-02", "2023-10-01", "S", 10200.0),
            new InsiderTransactionResponse.Transaction("Bob", 100, 38, "2023-10-02", "2023-10-01", "P", 10200.0),
            new InsiderTransactionResponse.Transaction("Bob", 100, 38, "2023-10-02", "2023-10-01", "P", 10200.0),
            new InsiderTransactionResponse.Transaction("Bob", 100, 38, "2023-10-02", "2023-10-01", "B", 10200.0)
        ));
        InsiderTransactionResponse responseGOOG = new InsiderTransactionResponse(List.of(
                new InsiderTransactionResponse.Transaction("John", 100, 12, "2023-10-02", "2023-10-01", "P", 10200.0),
                new InsiderTransactionResponse.Transaction("John", 100, 12, "2023-10-02", "2023-10-01", "S", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "B", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "P", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "P", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "B", 10200.0),
                new InsiderTransactionResponse.Transaction("Tim", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0)
        ));
        InsiderTransactionResponse responseAMZN = new InsiderTransactionResponse(List.of(
            new InsiderTransactionResponse.Transaction("Alice", 100, 10, "2023-10-02", "2023-10-01", "S", 10200.0),
            new InsiderTransactionResponse.Transaction("Bob", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0)
        ));
        InsiderTransactionResponse responseMETA = new InsiderTransactionResponse(List.of());
        InsiderTransactionResponse responseNVDA = new InsiderTransactionResponse(List.of());
        when(finnhubClient.getInsiderTransactions(StockSymbol.META)).thenReturn(responseMETA);
        when(finnhubClient.getInsiderTransactions(StockSymbol.AAPL)).thenReturn(responseAAPL);
        when(finnhubClient.getInsiderTransactions(StockSymbol.GOOG)).thenReturn(responseGOOG);
        when(finnhubClient.getInsiderTransactions(StockSymbol.AMZN)).thenReturn(responseAMZN);
        when(finnhubClient.getInsiderTransactions(StockSymbol.NVDA)).thenReturn(responseNVDA);

        Map<String, Integer> historicAAPL = Map.of("S", 42, "P", 10);
        Map<String, Integer> historicGOOG = Map.of("S", 2, "P", 5);
        Map<String, Integer> historicAMZN = Map.of("S", 2, "P", 2);
        Map<String, Integer> historicNVDA = Map.of("S", 21, "P", 0);
        List<InsiderTransactionHistoric> historicData = List.of(
            new InsiderTransactionHistoric(StockSymbol.AAPL, historicAAPL),
            new InsiderTransactionHistoric(StockSymbol.GOOG, historicGOOG),
            new InsiderTransactionHistoric(StockSymbol.AMZN, historicAMZN),
            new InsiderTransactionHistoric(StockSymbol.NVDA, historicNVDA)
        );
        when(insiderPersistence.readFromFile(anyString())).thenReturn(historicData);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        insiderTracker.trackInsiderTransactions();
        verify(telegramClient).sendMessage(reportCaptor.capture());
        String report = reportCaptor.getValue();

        verify(telegramClient, times(1)).sendMessage(anyString());
        verify(finnhubClient, times(5)).getInsiderTransactions(any(StockSymbol.class));
        verify(insiderPersistence, times(1)).readFromFile(anyString());
        verify(insiderPersistence, times(1)).persistToFile(any(), anyString());

        String expectedReport = """
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
        insiderTransactions.put(StockSymbol.PLTR, Map.of("S", 10, "S_HISTORIC", 0, "P", 0, "P_HISTORIC", 0));
        insiderTransactions.put(StockSymbol.GOOG, Map.of("S", 5, "S_HISTORIC", 10, "P", 3, "P_HISTORIC", 2, "P/V", 1));
        insiderTransactions.put(StockSymbol.AAPL, Map.of("S", 40, "S_HISTORIC", 20, "P", 5, "P_HISTORIC", 0));
        insiderTransactions.put(StockSymbol.HOOD, Map.of("S", 0, "S_HISTORIC", 15, "P", 43, "P_HISTORIC", 76, "P/V", 0, "S/V", 7));

        insiderTracker.sendInsiderTransactionReport(insiderTransactions);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        verify(telegramClient).sendMessage(reportCaptor.capture());
        String report = reportCaptor.getValue();

        String expectedReport = """
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
        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new EnumMap<>(StockSymbol.class);

        Map<String, Integer> aaplMap = new HashMap<>();
        aaplMap.put("S", 10);
        aaplMap.put("P", 5);

        Map<String, Integer> googMap = new HashMap<>();
        googMap.put("S", 3);
        googMap.put("P", 2);

        Map<String, Integer> metaMap = new HashMap<>();
        metaMap.put("S", 0);
        metaMap.put("P", 0);

        insiderTransactions.put(StockSymbol.AAPL, aaplMap);
        insiderTransactions.put(StockSymbol.GOOG, googMap);
        insiderTransactions.put(StockSymbol.META, metaMap);


        List<InsiderTransactionHistoric> historicData = new ArrayList<>();

        Map<String, Integer> map1 = new HashMap<>();
        map1.put("S", null);
        map1.put("P", null);
        historicData.add(new InsiderTransactionHistoric(StockSymbol.AAPL, map1));

        Map<String, Integer> map2 = new HashMap<>();
        map2.put("S", null);
        map2.put("P", null);
        historicData.add(new InsiderTransactionHistoric(StockSymbol.GOOG, map2));

        Map<String, Integer> map3 = new HashMap<>();
        map3.put("S", null);
        map3.put("P", null);
        historicData.add(new InsiderTransactionHistoric(StockSymbol.META, map3));


        when(insiderPersistence.readFromFile(InsiderPersistence.PERSISTENCE_FILE_PATH)).thenReturn(historicData);

        Map<StockSymbol, Map<String, Integer>> enrichedData = insiderTracker.enrichWithHistoricData(insiderTransactions);

        assertThat(enrichedData.get(StockSymbol.AAPL).get("S_HISTORIC"), is(0));
        assertThat(enrichedData.get(StockSymbol.AAPL).get("P_HISTORIC"), is(0));
        assertThat(enrichedData.get(StockSymbol.GOOG).get("S_HISTORIC"), is(0));
        assertThat(enrichedData.get(StockSymbol.GOOG).get("P_HISTORIC"), is(0));
        assertThat(enrichedData.get(StockSymbol.META).get("S_HISTORIC"), is(0));
        assertThat(enrichedData.get(StockSymbol.META).get("P_HISTORIC"), is(0));
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
        List<TargetPrice> targetPrices = List.of(
            new TargetPrice(StockSymbol.AAPL.getTicker(), 150.0, 300.0)
        );
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(finnhubClient.getInsiderTransactions(StockSymbol.AAPL)).thenReturn(new InsiderTransactionResponse(Collections.emptyList()));
        when(insiderPersistence.readFromFile(anyString())).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderPersistence).persistToFile(any(), anyString());
    }

    @Test
    void trackInsiderTransactions_noHistoricData() {
        List<TargetPrice> targetPrices = List.of(
            new TargetPrice(StockSymbol.AAPL.getTicker(), 150.0, 300.0)
        );
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        InsiderTransactionResponse responseAAPL = new InsiderTransactionResponse(List.of(
            new InsiderTransactionResponse.Transaction("Alice", 100, 5, "2023-10-02", "2023-10-01", "S", 10200.0)
        ));
        when(finnhubClient.getInsiderTransactions(StockSymbol.AAPL)).thenReturn(responseAAPL);
        when(insiderPersistence.readFromFile(anyString())).thenReturn(Collections.emptyList());

        insiderTracker.trackInsiderTransactions();

        verify(telegramClient).sendMessage(anyString());
        verify(insiderPersistence).persistToFile(any(), anyString());
    }
}
