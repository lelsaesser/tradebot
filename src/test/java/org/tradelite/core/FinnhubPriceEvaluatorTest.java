package org.tradelite.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class FinnhubPriceEvaluatorTest {

    @Mock private FinnhubClient finnhubClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private NotificationService notificationService;

    private FinnhubPriceEvaluator finnhubPriceEvaluator;

    @BeforeEach
    void setUp() {
        finnhubPriceEvaluator =
                new FinnhubPriceEvaluator(finnhubClient, targetPriceProvider, notificationService);
    }

    @Test
    void evaluatePrice() throws InterruptedException {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(StockSymbol.AVGO.getTicker(), 150.0, 160.0),
                        new TargetPrice(StockSymbol.GOOG.getTicker(), 130, 200));
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
        verify(notificationService, never()).sendNotification(any());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void evaluateHighPriceChange_success() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(false);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(notificationService, times(1))
                .sendNotification(contains(StockSymbol.AVGO.getDisplayName()));
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 5);
    }

    @Test
    void evaluateHighPriceChange_decrease_success() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(-6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(false);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(notificationService, times(1))
                .sendNotification(contains(StockSymbol.AVGO.getDisplayName()));
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 5);
    }

    @Test
    void evaluateHighPriceChange_noAlert() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(4.0);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(notificationService, never()).sendNotification(any());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void evaluateHighPriceChange_ignoredSymbol() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(true);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(notificationService, never()).sendNotification(any());
        verify(targetPriceProvider, never())
                .addIgnoredSymbol(any(StockSymbol.class), any(IgnoreReason.class), anyInt());
    }

    @Test
    void evaluateHighPriceChange_multipleThresholds() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(11.0);

        when(targetPriceProvider.isSymbolIgnored(
                        StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 10))
                .thenReturn(false);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(notificationService, times(1))
                .sendNotification(contains(StockSymbol.AVGO.getDisplayName()));
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 10);
    }

    @Test
    void evaluateHighPriceChange_thresholdAlreadyAlerted() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.AVGO);
        priceQuoteResponse.setChangePercent(11.0);

        when(targetPriceProvider.isSymbolIgnored(
                        StockSymbol.AVGO, IgnoreReason.CHANGE_PERCENT_ALERT, 10))
                .thenReturn(true);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(notificationService, never()).sendNotification(any());
        verify(targetPriceProvider, never())
                .addIgnoredSymbol(any(StockSymbol.class), any(IgnoreReason.class), anyInt());
    }

    @Test
    void evaluatePrice_priceDidNotChange() throws InterruptedException {
        double lastPrice = Math.random();

        for (StockSymbol symbol : StockSymbol.getAll()) {
            finnhubPriceEvaluator.lastPriceCache.put(symbol, lastPrice);
        }

        List<TargetPrice> targetPrices = new ArrayList<>();
        for (StockSymbol symbol : StockSymbol.getAll()) {
            targetPrices.add(
                    new TargetPrice(symbol.getTicker(), lastPrice - 1000, lastPrice + 1000));
        }
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setCurrentPrice(lastPrice);

        when(finnhubClient.getPriceQuote(any())).thenReturn(priceQuoteResponse);

        int finDataSize = finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(StockSymbol.getAll().size())).getPriceQuote(any());

        assertThat(finnhubPriceEvaluator.lastPriceCache, aMapWithSize(StockSymbol.getAll().size()));
        assertThat(finDataSize, is(0));
    }

    @Test
    void evaluatePrice_invalidTickerSymbolInTargetPrice_doesNotSendMessage()
            throws InterruptedException {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice("INVALID", 150.0, 160.0),
                        new TargetPrice(StockSymbol.GOOG.getTicker(), 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.GOOG);
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(1)).getPriceQuote(StockSymbol.GOOG);
        verify(notificationService, never()).sendNotification(anyString());
    }

    @Test
    void evaluatePrice_nullPriceQuote() throws InterruptedException {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(StockSymbol.AVGO.getTicker(), 150.0, 160.0),
                        new TargetPrice(StockSymbol.GOOG.getTicker(), 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        when(finnhubClient.getPriceQuote(StockSymbol.AVGO)).thenReturn(null);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(StockSymbol.GOOG);
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(StockSymbol.GOOG)).thenReturn(priceQuoteResponse);

        int finDataSize = finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(1)).getPriceQuote(StockSymbol.AVGO);
        verify(finnhubClient, times(1)).getPriceQuote(StockSymbol.GOOG);
        assertThat(finDataSize, is(1));
    }

    @Test
    void comparePrices_zeroSellTarget() {
        finnhubPriceEvaluator.comparePrices(StockSymbol.AVGO, 200.0, 150.0, 0.0);
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void comparePrices_zeroBuyTarget() {
        finnhubPriceEvaluator.comparePrices(StockSymbol.AVGO, 100.0, 0.0, 150.0);
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void comparePrices_sellAlertIgnored() {
        when(targetPriceProvider.isSymbolIgnored(StockSymbol.AVGO, IgnoreReason.SELL_ALERT))
                .thenReturn(true);
        finnhubPriceEvaluator.comparePrices(StockSymbol.AVGO, 200.0, 150.0, 180.0);
        verify(notificationService, never()).sendNotification(any());
    }

    @Test
    void comparePrices_buyAlertIgnored() {
        when(targetPriceProvider.isSymbolIgnored(StockSymbol.AVGO, IgnoreReason.BUY_ALERT))
                .thenReturn(true);
        finnhubPriceEvaluator.comparePrices(StockSymbol.AVGO, 100.0, 120.0, 150.0);
        verify(notificationService, never()).sendNotification(any());
    }
}
