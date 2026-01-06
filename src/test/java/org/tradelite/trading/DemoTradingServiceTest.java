package org.tradelite.trading;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.trading.model.Portfolio;

@ExtendWith(MockitoExtension.class)
class DemoTradingServiceTest {

    @Mock private PortfolioPersistence portfolioPersistence;

    @Mock private TelegramClient telegramClient;

    @InjectMocks private DemoTradingService demoTradingService;

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = Portfolio.createInitial(100000.0);
    }

    @Test
    void executeBuy_shouldPurchaseAndSendNotification() {
        when(portfolioPersistence.loadPortfolio()).thenReturn(portfolio);

        demoTradingService.executeBuy(StockSymbol.AAPL, 150.0, "Target price reached");

        verify(portfolioPersistence, times(1)).loadPortfolio();
        verify(portfolioPersistence, times(1)).savePortfolio(any(Portfolio.class));
        verify(portfolioPersistence, times(1)).addTransaction(any());
        verify(telegramClient, times(1)).sendMessage(contains("Demo Trade Executed: BUY"));
        verify(telegramClient, times(1)).sendMessage(contains("Apple (AAPL)"));
    }

    @Test
    void executeBuy_insufficientFunds_shouldNotifyFailure() {
        Portfolio poorPortfolio = Portfolio.createInitial(10.0);
        when(portfolioPersistence.loadPortfolio()).thenReturn(poorPortfolio);

        demoTradingService.executeBuy(StockSymbol.AAPL, 150.0, "Target price reached");

        verify(portfolioPersistence, times(1)).loadPortfolio();
        verify(portfolioPersistence, never()).savePortfolio(any());
        verify(portfolioPersistence, never()).addTransaction(any());
        verify(telegramClient, times(1)).sendMessage(contains("Demo Trade Failed"));
    }

    @Test
    void executeBuy_persistenceThrowsException_shouldHandleGracefully() {
        when(portfolioPersistence.loadPortfolio()).thenThrow(new RuntimeException("IO Error"));

        demoTradingService.executeBuy(StockSymbol.AAPL, 150.0, "Target price reached");

        verify(portfolioPersistence, times(1)).loadPortfolio();
        verify(portfolioPersistence, never()).savePortfolio(any());
        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void executeSell_withPosition_shouldSellAndNotify() {
        Portfolio withPosition = portfolio.buy("AAPL", 10.0, 140.0);
        when(portfolioPersistence.loadPortfolio()).thenReturn(withPosition);

        demoTradingService.executeSell(StockSymbol.AAPL, 160.0, "Target price reached");

        verify(portfolioPersistence, times(1)).loadPortfolio();
        verify(portfolioPersistence, times(1)).savePortfolio(any(Portfolio.class));
        verify(portfolioPersistence, times(1)).addTransaction(any());
        verify(telegramClient, times(1)).sendMessage(contains("Demo Trade Executed: SELL"));
        verify(telegramClient, times(1)).sendMessage(contains("Profit/Loss"));
    }

    @Test
    void executeSell_noPosition_shouldNotExecute() {
        when(portfolioPersistence.loadPortfolio()).thenReturn(portfolio);

        demoTradingService.executeSell(StockSymbol.AAPL, 160.0, "Target price reached");

        verify(portfolioPersistence, times(1)).loadPortfolio();
        verify(portfolioPersistence, never()).savePortfolio(any());
        verify(portfolioPersistence, never()).addTransaction(any());
        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void executeSell_persistenceThrowsException_shouldHandleGracefully() {
        when(portfolioPersistence.loadPortfolio()).thenThrow(new RuntimeException("IO Error"));

        demoTradingService.executeSell(StockSymbol.AAPL, 160.0, "Target price reached");

        verify(portfolioPersistence, times(1)).loadPortfolio();
        verify(portfolioPersistence, never()).savePortfolio(any());
        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void getPortfolio_shouldReturnLoadedPortfolio() {
        when(portfolioPersistence.loadPortfolio()).thenReturn(portfolio);

        Portfolio result = demoTradingService.getPortfolio();

        verify(portfolioPersistence, times(1)).loadPortfolio();
        assert result.equals(portfolio);
    }

    @Test
    void executeBuy_withProfit_shouldShowCorrectDetails() {
        when(portfolioPersistence.loadPortfolio()).thenReturn(portfolio);

        demoTradingService.executeBuy(StockSymbol.AAPL, 200.0, "Buy signal");

        verify(telegramClient, times(1)).sendMessage(contains("Quantity: 1.0"));
        verify(telegramClient, times(1)).sendMessage(contains("Price: $200.00"));
        verify(telegramClient, times(1)).sendMessage(contains("Total Cost: $200.00"));
    }

    @Test
    void executeSell_withLoss_shouldShowNegativeProfit() {
        Portfolio withPosition = portfolio.buy("AAPL", 10.0, 160.0);
        when(portfolioPersistence.loadPortfolio()).thenReturn(withPosition);

        demoTradingService.executeSell(StockSymbol.AAPL, 140.0, "Sell signal");

        verify(telegramClient, times(1)).sendMessage(contains("SELL"));
        verify(telegramClient, times(1)).sendMessage(contains("Profit/Loss"));
    }
}
