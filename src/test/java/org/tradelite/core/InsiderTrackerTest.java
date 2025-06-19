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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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

    private InsiderTracker insiderTracker;

    @BeforeEach
    void setUp() {
        insiderTracker = new InsiderTracker(finnhubClient, telegramClient, targetPriceProvider);
    }

    @Test
    void trackInsiderTransactions_shouldFetchAndSendReport() {
        List<TargetPrice> targetPrices = List.of(
            new TargetPrice(StockSymbol.AAPL.getTicker(), 150.0, 300.0),
            new TargetPrice(StockSymbol.GOOG.getTicker(), 200.0, 400.0),
            new TargetPrice(StockSymbol.META.getTicker(), 200.0, 400.0)
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
        InsiderTransactionResponse responseMETA = new InsiderTransactionResponse(List.of());
        when(finnhubClient.getInsiderTransactions(StockSymbol.META)).thenReturn(responseMETA);
        when(finnhubClient.getInsiderTransactions(StockSymbol.AAPL)).thenReturn(responseAAPL);
        when(finnhubClient.getInsiderTransactions(StockSymbol.GOOG)).thenReturn(responseGOOG);

        ArgumentCaptor<String> reportCaptor = ArgumentCaptor.forClass(String.class);
        insiderTracker.trackInsiderTransactions();
        verify(telegramClient).sendMessage(reportCaptor.capture());
        String report = reportCaptor.getValue();

        assertThat(report, containsString("AAPL: 2 sells"));
        assertThat(report, containsString("GOOG: 5 sells"));
        assertThat(report, not(containsString("META")));
    }


}
