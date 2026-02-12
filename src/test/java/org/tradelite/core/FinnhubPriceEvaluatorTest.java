package org.tradelite.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

@ExtendWith(MockitoExtension.class)
class FinnhubPriceEvaluatorTest {

    @Mock private FinnhubClient finnhubClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private org.tradelite.service.StockSymbolRegistry stockSymbolRegistry;

    private FinnhubPriceEvaluator finnhubPriceEvaluator;

    @BeforeEach
    void setUp() {
        finnhubPriceEvaluator =
                new FinnhubPriceEvaluator(
                        finnhubClient, targetPriceProvider, telegramClient, stockSymbolRegistry);
    }

    @Test
    void evaluatePrice() throws InterruptedException {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(
                                new StockSymbol("AVGO", "Broadcom").getTicker(), 150.0, 160.0),
                        new TargetPrice(new StockSymbol("GOOG", "Google").getTicker(), 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(stockSymbolRegistry.fromString("AVGO"))
                .thenReturn(java.util.Optional.of(new StockSymbol("AVGO", "Broadcom")));
        when(stockSymbolRegistry.fromString("GOOG"))
                .thenReturn(java.util.Optional.of(new StockSymbol("GOOG", "Google")));

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("AVGO", "Broadcom"));
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(1)).getPriceQuote(new StockSymbol("AVGO", "Broadcom"));
        verify(finnhubClient, times(1)).getPriceQuote(new StockSymbol("GOOG", "Google"));
        verify(telegramClient, never()).sendMessage(any());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void evaluateHighPriceChange_success() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("AVGO", "Broadcom"));
        priceQuoteResponse.setChangePercent(6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(false);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, times(1))
                .sendMessage(contains(new StockSymbol("AVGO", "Broadcom").getDisplayName()));
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 5);
    }

    @Test
    void evaluateHighPriceChange_decrease_success() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("AVGO", "Broadcom"));
        priceQuoteResponse.setChangePercent(-6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(false);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, times(1))
                .sendMessage(contains(new StockSymbol("AVGO", "Broadcom").getDisplayName()));
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 5);
    }

    @Test
    void evaluateHighPriceChange_noAlert() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("AVGO", "Broadcom"));
        priceQuoteResponse.setChangePercent(4.0);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, never()).sendMessage(any());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void evaluateHighPriceChange_ignoredSymbol() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("AVGO", "Broadcom"));
        priceQuoteResponse.setChangePercent(6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(true);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, never()).sendMessage(any());
        verify(targetPriceProvider, never())
                .addIgnoredSymbol(any(StockSymbol.class), any(IgnoreReason.class), anyInt());
    }

    @Test
    void evaluateHighPriceChange_multipleThresholds() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("AVGO", "Broadcom"));
        priceQuoteResponse.setChangePercent(11.0);

        when(targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 10))
                .thenReturn(false);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, times(1))
                .sendMessage(contains(new StockSymbol("AVGO", "Broadcom").getDisplayName()));
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 10);
    }

    @Test
    void evaluateHighPriceChange_thresholdAlreadyAlerted() {
        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("AVGO", "Broadcom"));
        priceQuoteResponse.setChangePercent(11.0);

        when(targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.CHANGE_PERCENT_ALERT, 10))
                .thenReturn(true);

        finnhubPriceEvaluator.evaluateHighPriceChange(priceQuoteResponse);

        verify(telegramClient, never()).sendMessage(any());
        verify(targetPriceProvider, never())
                .addIgnoredSymbol(any(StockSymbol.class), any(IgnoreReason.class), anyInt());
    }

    @Test
    void evaluatePrice_priceDidNotChange() throws InterruptedException {
        double lastPrice = 150.0;
        StockSymbol testSymbol = new StockSymbol("TEST", "Test Company");

        finnhubPriceEvaluator.lastPriceCache.put("TEST", lastPrice);

        List<TargetPrice> targetPrices =
                List.of(new TargetPrice("TEST", lastPrice - 1000, lastPrice + 1000));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(stockSymbolRegistry.fromString("TEST")).thenReturn(java.util.Optional.of(testSymbol));

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setCurrentPrice(lastPrice);
        priceQuoteResponse.setStockSymbol(testSymbol);

        when(finnhubClient.getPriceQuote(any())).thenReturn(priceQuoteResponse);

        int finDataSize = finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(1)).getPriceQuote(any());

        assertThat(finnhubPriceEvaluator.lastPriceCache, aMapWithSize(1));
        assertThat(finDataSize, is(0));
    }

    @Test
    void evaluatePrice_invalidTickerSymbolInTargetPrice_doesNotSendMessage()
            throws InterruptedException {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice("INVALID", 150.0, 160.0),
                        new TargetPrice(new StockSymbol("GOOG", "Google").getTicker(), 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(stockSymbolRegistry.fromString("INVALID")).thenReturn(java.util.Optional.empty());
        when(stockSymbolRegistry.fromString("GOOG"))
                .thenReturn(java.util.Optional.of(new StockSymbol("GOOG", "Google")));

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("GOOG", "Google"));
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(1)).getPriceQuote(new StockSymbol("GOOG", "Google"));
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void evaluatePrice_nullPriceQuote() throws InterruptedException {
        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice(
                                new StockSymbol("AVGO", "Broadcom").getTicker(), 150.0, 160.0),
                        new TargetPrice(new StockSymbol("GOOG", "Google").getTicker(), 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(stockSymbolRegistry.fromString("AVGO"))
                .thenReturn(java.util.Optional.of(new StockSymbol("AVGO", "Broadcom")));
        when(stockSymbolRegistry.fromString("GOOG"))
                .thenReturn(java.util.Optional.of(new StockSymbol("GOOG", "Google")));

        when(finnhubClient.getPriceQuote(new StockSymbol("AVGO", "Broadcom"))).thenReturn(null);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(new StockSymbol("GOOG", "Google"));
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(new StockSymbol("GOOG", "Google")))
                .thenReturn(priceQuoteResponse);

        int finDataSize = finnhubPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getStockTargetPrices();
        verify(finnhubClient, times(1)).getPriceQuote(new StockSymbol("AVGO", "Broadcom"));
        verify(finnhubClient, times(1)).getPriceQuote(new StockSymbol("GOOG", "Google"));
        assertThat(finDataSize, is(1));
    }

    @Test
    void comparePrices_zeroSellTarget() {
        finnhubPriceEvaluator.comparePrices(new StockSymbol("AVGO", "Broadcom"), 200.0, 150.0, 0.0);
        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void comparePrices_zeroBuyTarget() {
        finnhubPriceEvaluator.comparePrices(new StockSymbol("AVGO", "Broadcom"), 100.0, 0.0, 150.0);
        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void comparePrices_sellAlertIgnored() {
        when(targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.SELL_ALERT))
                .thenReturn(true);
        finnhubPriceEvaluator.comparePrices(
                new StockSymbol("AVGO", "Broadcom"), 200.0, 150.0, 180.0);
        verify(telegramClient, never()).sendMessage(any());
    }

    @Test
    void comparePrices_buyAlertIgnored() {
        when(targetPriceProvider.isSymbolIgnored(
                        new StockSymbol("AVGO", "Broadcom"), IgnoreReason.BUY_ALERT))
                .thenReturn(true);
        finnhubPriceEvaluator.comparePrices(
                new StockSymbol("AVGO", "Broadcom"), 100.0, 120.0, 150.0);
        verify(telegramClient, never()).sendMessage(any());
    }
}
