package org.tradelite.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinnhubPriceEvaluatorTest {

    @Mock
    private FinnhubClient finnhubClient;
    @Mock
    private TargetPriceProvider targetPriceProvider;
    @Mock
    private TelegramClient telegramClient;

    private FinnhubPriceEvaluator finnhubPriceEvaluator;

    @BeforeEach
    void setUp() {
        finnhubPriceEvaluator = new FinnhubPriceEvaluator(finnhubClient, targetPriceProvider, telegramClient);
    }

    @Test
    void evaluatePrice() throws InterruptedException {
        List<TargetPrice> targetPrices = List.of(
                new TargetPrice(StockSymbol.AVGO.getTicker(), 150.0, 160.0),
                new TargetPrice(StockSymbol.GOOG.getTicker(), 130, 200)
        );
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(1)).getPriceQuote(StockSymbol.AVGO);
        verify(finnhubClient, times(1)).getPriceQuote(StockSymbol.GOOG);
        verify(telegramClient, never()).sendMessage(any());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void evaluateHighPriceChange_success() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(6.0);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, times(1)).sendMessage("⚠️ High daily price swing detected for AVGO: 6.0%");
        verify(targetPriceProvider, times(1)).addIgnoredSymbol(StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT);
    }

    @Test
    void evaluateHighPriceChange_noAlert() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(4.0);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, never()).sendMessage(any());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void evaluateHighPriceChange_ignoredSymbol() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(6.0);

        when(targetPriceProvider.isSymbolIgnored(StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT)).thenReturn(true);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, never()).sendMessage(any());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }
}
