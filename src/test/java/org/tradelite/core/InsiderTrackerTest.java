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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
            new InsiderTransactionResponse.Transaction("Alice", 100, 10, "2023-10-02", "2023-10-01", "P", 10200.0),
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
        Map<String, Integer> historicAMZN = Map.of("S", 2, "P", 0);
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
        AAPL         2            -40        \s
        AMZN         2            0          \s
        NVDA         0            -21        \s
        ```
        
        ```
        Symbol       Buys         Diff       \s
        GOOG         3            -2         \s
        AAPL         2            -8         \s
        AMZN         1            +1         \s
        ```""";

        assertThat(report, is(expectedReport));
    }

    @Test
    void sendInsiderTransactionReport() {
        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new LinkedHashMap<>();
        insiderTransactions.put(StockSymbol.PLTR, Map.of("S", 10, "S_HISTORIC", 0, "P", 0, "P_HISTORIC", 0));
        insiderTransactions.put(StockSymbol.GOOG, Map.of("S", 5, "S_HISTORIC", 10, "P", 3, "P_HISTORIC", 2));
        insiderTransactions.put(StockSymbol.AAPL, Map.of("S", 40, "S_HISTORIC", 20, "P", 5, "P_HISTORIC", 0));
        insiderTransactions.put(StockSymbol.HOOD, Map.of("S", 0, "S_HISTORIC", 15, "P", 43, "P_HISTORIC", 76));

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
        HOOD         0            -15        \s
        ```
        
        ```
        Symbol       Buys         Diff       \s
        AAPL         5            +5         \s
        GOOG         3            +1         \s
        HOOD         43           -33        \s
        ```""";

        assertThat(report, is(expectedReport));
    }

}
