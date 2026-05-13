package org.tradelite.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.client.yahoo.YahooFetchException;
import org.tradelite.client.yahoo.YahooFinanceClient;
import org.tradelite.client.yahoo.YahooPriceQuote;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.LivePriceCache;
import org.tradelite.service.MarketStatusService;

@ExtendWith(MockitoExtension.class)
class YahooPriceEvaluatorTest {

    @Mock private YahooFinanceClient yahooFinanceClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;
    @Mock private SymbolRegistry symbolRegistry;
    @Mock private PriceQuoteRepository priceQuoteRepository;
    @Mock private FeatureToggleService featureToggleService;
    @Mock private MarketStatusService marketStatusService;

    private LivePriceCache livePriceCache;
    private YahooPriceEvaluator evaluator;

    private static final StockSymbol RHM = new StockSymbol("RHM.DE", "Rheinmetall AG");
    private static final StockSymbol SAMSUNG = new StockSymbol("005930.KS", "Samsung Electronics");

    @BeforeEach
    void setUp() {
        livePriceCache = new LivePriceCache();
        evaluator =
                new YahooPriceEvaluator(
                        yahooFinanceClient,
                        targetPriceProvider,
                        telegramClient,
                        symbolRegistry,
                        priceQuoteRepository,
                        featureToggleService,
                        marketStatusService,
                        livePriceCache);
    }

    @Test
    void evaluatePrice_populatesCache() throws InterruptedException {
        when(symbolRegistry.getInternationalStocks()).thenReturn(List.of(RHM));
        when(marketStatusService.isExchangeOpen("RHM.DE")).thenReturn(true);
        when(yahooFinanceClient.fetchCurrentPrice("RHM.DE"))
                .thenReturn(
                        new YahooPriceQuote(
                                "RHM.DE", 1200.0, 1180.0, 1190.0, 1220.0, 1170.0, 1.69, 0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());

        int updated = evaluator.evaluatePrice();

        assertThat(updated, is(1));
        assertThat(livePriceCache.get("RHM.DE"), is(1200.0));
    }

    @Test
    void evaluatePrice_skipsWhenExchangeClosed() throws InterruptedException {
        when(symbolRegistry.getInternationalStocks()).thenReturn(List.of(RHM));
        when(marketStatusService.isExchangeOpen("RHM.DE")).thenReturn(false);
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());

        int updated = evaluator.evaluatePrice();

        assertThat(updated, is(0));
        assertThat(livePriceCache.get("RHM.DE"), is(nullValue()));
        verify(yahooFinanceClient, never()).fetchCurrentPrice(anyString());
    }

    @Test
    void evaluatePrice_skipsWhenPriceUnchanged() throws InterruptedException {
        livePriceCache.put("RHM.DE", 1200.0);
        when(symbolRegistry.getInternationalStocks()).thenReturn(List.of(RHM));
        when(marketStatusService.isExchangeOpen("RHM.DE")).thenReturn(true);
        when(yahooFinanceClient.fetchCurrentPrice("RHM.DE"))
                .thenReturn(
                        new YahooPriceQuote(
                                "RHM.DE", 1200.0, 1180.0, 1190.0, 1220.0, 1170.0, 1.69, 0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());

        int updated = evaluator.evaluatePrice();

        assertThat(updated, is(0));
    }

    @Test
    void evaluatePrice_continuesOnFetchException() throws InterruptedException {
        when(symbolRegistry.getInternationalStocks()).thenReturn(List.of(RHM, SAMSUNG));
        when(marketStatusService.isExchangeOpen("RHM.DE")).thenReturn(true);
        when(marketStatusService.isExchangeOpen("005930.KS")).thenReturn(true);
        when(yahooFinanceClient.fetchCurrentPrice("RHM.DE"))
                .thenThrow(new YahooFetchException("RHM.DE", "timeout"));
        when(yahooFinanceClient.fetchCurrentPrice("005930.KS"))
                .thenReturn(
                        new YahooPriceQuote(
                                "005930.KS",
                                285000.0,
                                280000.0,
                                282000.0,
                                290000.0,
                                278000.0,
                                1.78,
                                0));
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());

        int updated = evaluator.evaluatePrice();

        assertThat(updated, is(1));
        assertThat(livePriceCache.get("RHM.DE"), is(nullValue()));
        assertThat(livePriceCache.get("005930.KS"), is(285000.0));
    }

    @Test
    void evaluatePrice_persistsWhenFeatureEnabled() throws InterruptedException {
        when(symbolRegistry.getInternationalStocks()).thenReturn(List.of(RHM));
        when(marketStatusService.isExchangeOpen("RHM.DE")).thenReturn(true);
        when(yahooFinanceClient.fetchCurrentPrice("RHM.DE"))
                .thenReturn(
                        new YahooPriceQuote(
                                "RHM.DE", 1200.0, 1180.0, 1190.0, 1220.0, 1170.0, 1.69, 0));
        when(featureToggleService.isEnabled(FeatureToggle.FINNHUB_PRICE_COLLECTION))
                .thenReturn(true);
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of());

        evaluator.evaluatePrice();

        verify(priceQuoteRepository, times(1)).save(any());
    }

    @Test
    void evaluatePrice_evaluatesTargetPrices() throws InterruptedException {
        livePriceCache.put("RHM.DE", 1200.0);
        when(symbolRegistry.getInternationalStocks()).thenReturn(List.of());
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("RHM.DE", 1250.0, 1500.0)));
        when(symbolRegistry.isInternationalSymbol("RHM.DE")).thenReturn(true);
        when(symbolRegistry.fromString("RHM.DE")).thenReturn(Optional.of(RHM));

        evaluator.evaluatePrice();

        // Price 1200 <= buyTarget 1250 → should trigger buy alert
        verify(telegramClient, times(1)).sendMessage(contains("Rheinmetall"));
    }

    @Test
    void evaluateHighPriceChange_sendsAlertAbove5Percent() {
        YahooPriceQuote quote =
                new YahooPriceQuote("RHM.DE", 1200.0, 1100.0, 1110.0, 1220.0, 1170.0, 9.09, 0);

        evaluator.evaluateHighPriceChange(RHM, quote);

        verify(telegramClient, times(1)).sendMessage(contains("Rheinmetall"));
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(RHM, IgnoreReason.CHANGE_PERCENT_ALERT, 5);
    }

    @Test
    void evaluateHighPriceChange_skipsBelow5Percent() {
        YahooPriceQuote quote =
                new YahooPriceQuote("RHM.DE", 1200.0, 1180.0, 1190.0, 1220.0, 1170.0, 1.69, 0);

        evaluator.evaluateHighPriceChange(RHM, quote);

        verify(telegramClient, never()).sendMessage(anyString());
    }
}
