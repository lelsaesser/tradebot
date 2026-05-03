package org.tradelite.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
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
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.MarketHolidayService;

@ExtendWith(MockitoExtension.class)
class FinnhubPriceEvaluatorTest {

    @Mock private FinnhubClient finnhubClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private org.tradelite.common.SymbolRegistry symbolRegistry;
    @Mock private PriceQuoteRepository priceQuoteRepository;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private MarketHolidayService marketHolidayService;

    private FinnhubPriceEvaluator finnhubPriceEvaluator;

    @BeforeEach
    void setUp() {
        finnhubPriceEvaluator =
                new FinnhubPriceEvaluator(
                        finnhubClient,
                        targetPriceProvider,
                        telegramClient,
                        symbolRegistry,
                        priceQuoteRepository,
                        featureToggleService,
                        marketHolidayService);
    }

    @Test
    void evaluatePrice() throws InterruptedException {
        StockSymbol avgo = new StockSymbol("AVGO", "Broadcom");
        StockSymbol goog = new StockSymbol("GOOG", "Google");
        when(symbolRegistry.getAll()).thenReturn(List.of(avgo, goog));

        List<TargetPrice> targetPrices =
                List.of(new TargetPrice("AVGO", 150.0, 160.0), new TargetPrice("GOOG", 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(symbolRegistry.fromString("AVGO")).thenReturn(java.util.Optional.of(avgo));
        when(symbolRegistry.fromString("GOOG")).thenReturn(java.util.Optional.of(goog));

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(avgo);
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(symbolRegistry, times(1)).getAll();
        verify(finnhubClient, times(1)).getPriceQuote(avgo);
        verify(finnhubClient, times(1)).getPriceQuote(goog);
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

        when(symbolRegistry.getAll()).thenReturn(List.of(testSymbol));
        List<TargetPrice> targetPrices =
                List.of(new TargetPrice("TEST", lastPrice - 1000, lastPrice + 1000));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setCurrentPrice(lastPrice);
        priceQuoteResponse.setStockSymbol(testSymbol);

        when(finnhubClient.getPriceQuote(any())).thenReturn(priceQuoteResponse);

        int finDataSize = finnhubPriceEvaluator.evaluatePrice();

        verify(symbolRegistry, times(1)).getAll();
        verify(finnhubClient, times(1)).getPriceQuote(any());

        assertThat(finnhubPriceEvaluator.lastPriceCache, aMapWithSize(1));
        assertThat(finDataSize, is(0));
    }

    @Test
    void evaluatePrice_invalidTickerSymbolInTargetPrice_doesNotSendMessage()
            throws InterruptedException {
        StockSymbol goog = new StockSymbol("GOOG", "Google");
        when(symbolRegistry.getAll()).thenReturn(List.of(goog));

        List<TargetPrice> targetPrices =
                List.of(
                        new TargetPrice("INVALID", 150.0, 160.0),
                        new TargetPrice("GOOG", 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(symbolRegistry.fromString("GOOG")).thenReturn(java.util.Optional.of(goog));

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(goog);
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(symbolRegistry, times(1)).getAll();
        verify(finnhubClient, times(1)).getPriceQuote(goog);
        verify(telegramClient, never()).sendMessage(anyString());
    }

    @Test
    void evaluatePrice_nullPriceQuote() throws InterruptedException {
        StockSymbol avgo = new StockSymbol("AVGO", "Broadcom");
        StockSymbol goog = new StockSymbol("GOOG", "Google");
        when(symbolRegistry.getAll()).thenReturn(List.of(avgo, goog));

        List<TargetPrice> targetPrices =
                List.of(new TargetPrice("AVGO", 150.0, 160.0), new TargetPrice("GOOG", 130, 200));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(symbolRegistry.fromString("GOOG")).thenReturn(java.util.Optional.of(goog));

        when(finnhubClient.getPriceQuote(avgo)).thenReturn(null);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(goog);
        priceQuoteResponse.setCurrentPrice(155.0);
        priceQuoteResponse.setChangePercent(3.0);
        when(finnhubClient.getPriceQuote(goog)).thenReturn(priceQuoteResponse);

        int finDataSize = finnhubPriceEvaluator.evaluatePrice();

        verify(symbolRegistry, times(1)).getAll();
        verify(finnhubClient, times(1)).getPriceQuote(avgo);
        verify(finnhubClient, times(1)).getPriceQuote(goog);
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

    @Test
    void evaluatePrice_savesPriceQuoteToRepository_whenToggleEnabled() throws InterruptedException {
        StockSymbol testSymbol = new StockSymbol("AAPL", "Apple Inc.");
        when(symbolRegistry.getAll()).thenReturn(List.of(testSymbol));
        List<TargetPrice> targetPrices = List.of(new TargetPrice("AAPL", 150.0, 200.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(symbolRegistry.fromString("AAPL")).thenReturn(java.util.Optional.of(testSymbol));
        when(featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION))
                .thenReturn(true);
        when(marketHolidayService.isMarketOpen(null)).thenReturn(true);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(testSymbol);
        priceQuoteResponse.setCurrentPrice(175.0);
        priceQuoteResponse.setChangePercent(1.5);
        when(finnhubClient.getPriceQuote(testSymbol)).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(priceQuoteRepository, times(1)).save(priceQuoteResponse);
    }

    @Test
    void evaluatePrice_doesNotSavePriceQuote_whenToggleDisabled() throws InterruptedException {
        StockSymbol testSymbol = new StockSymbol("AAPL", "Apple Inc.");
        when(symbolRegistry.getAll()).thenReturn(List.of(testSymbol));
        List<TargetPrice> targetPrices = List.of(new TargetPrice("AAPL", 150.0, 200.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);
        when(symbolRegistry.fromString("AAPL")).thenReturn(java.util.Optional.of(testSymbol));
        when(featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION))
                .thenReturn(false);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(testSymbol);
        priceQuoteResponse.setCurrentPrice(175.0);
        priceQuoteResponse.setChangePercent(1.5);
        when(finnhubClient.getPriceQuote(testSymbol)).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(priceQuoteRepository, never()).save(any());
    }

    @Test
    void evaluatePrice_doesNotSavePriceQuoteWhenPriceUnchanged() throws InterruptedException {
        StockSymbol testSymbol = new StockSymbol("AAPL", "Apple Inc.");
        finnhubPriceEvaluator.lastPriceCache.put("AAPL", 175.0);

        when(symbolRegistry.getAll()).thenReturn(List.of(testSymbol));
        List<TargetPrice> targetPrices = List.of(new TargetPrice("AAPL", 150.0, 200.0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(targetPrices);

        PriceQuoteResponse priceQuoteResponse = new PriceQuoteResponse();
        priceQuoteResponse.setStockSymbol(testSymbol);
        priceQuoteResponse.setCurrentPrice(175.0);
        when(finnhubClient.getPriceQuote(testSymbol)).thenReturn(priceQuoteResponse);

        finnhubPriceEvaluator.evaluatePrice();

        verify(priceQuoteRepository, never()).save(any());
    }

    @Test
    void evaluatePrice_cachesAllSymbolsNotJustTargetPriceStocks() throws InterruptedException {
        StockSymbol aapl = new StockSymbol("AAPL", "Apple Inc.");
        StockSymbol tsm = new StockSymbol("TSM", "Taiwan Semiconductor");
        when(symbolRegistry.getAll()).thenReturn(List.of(aapl, tsm));

        // Only AAPL has a target price, TSM does not
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("AAPL", 150.0, 200.0)));
        when(symbolRegistry.fromString("AAPL")).thenReturn(java.util.Optional.of(aapl));

        PriceQuoteResponse aaplQuote = new PriceQuoteResponse();
        aaplQuote.setStockSymbol(aapl);
        aaplQuote.setCurrentPrice(175.0);
        aaplQuote.setChangePercent(1.5);
        when(finnhubClient.getPriceQuote(aapl)).thenReturn(aaplQuote);

        PriceQuoteResponse tsmQuote = new PriceQuoteResponse();
        tsmQuote.setStockSymbol(tsm);
        tsmQuote.setCurrentPrice(403.0);
        tsmQuote.setChangePercent(2.0);
        when(finnhubClient.getPriceQuote(tsm)).thenReturn(tsmQuote);

        finnhubPriceEvaluator.evaluatePrice();

        // Both symbols should be cached, even though TSM has no target price
        assertThat(finnhubPriceEvaluator.lastPriceCache, aMapWithSize(2));
        assertThat(finnhubPriceEvaluator.lastPriceCache.get("AAPL"), is(175.0));
        assertThat(finnhubPriceEvaluator.lastPriceCache.get("TSM"), is(403.0));
    }
}
