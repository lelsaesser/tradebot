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

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            new InsiderTransactionResponse.Transaction("Bob", 100, 38, "2023-10-02", "2023-10-01", "B", 10200.0)
        ));
        InsiderTransactionResponse responseGOOG = new InsiderTransactionResponse(List.of(
                new InsiderTransactionResponse.Transaction("John", 100, 12, "2023-10-02", "2023-10-01", "S", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "B", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
                new InsiderTransactionResponse.Transaction("Doe", 100, 20, "2023-10-02", "2023-10-01", "S", 10200.0),
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

        Map<String, Integer> historicAAPL = Map.of("S", 42);
        Map<String, Integer> historicGOOG = Map.of("S", 2);
        Map<String, Integer> historicAMZN = Map.of("S", 2);
        Map<String, Integer> historicNVDA = Map.of("S", 21);
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

        assertThat(report, containsString("AAPL: 2 sells (-40)"));
        assertThat(report, containsString("GOOG: 5 sells (+3)"));
        assertThat(report, not(containsString("META")));
        assertThat(report, containsString("AMZN: 2 sells (+-0)"));
        assertThat(report, containsString("NVDA: 0 sells (-21)"));
    }

}
