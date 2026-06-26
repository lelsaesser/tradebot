package org.tradelite.service;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.client.yahoo.YahooFinanceClient;
import org.tradelite.common.AssetType;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.NewlyAddedSymbolRepository;
import org.tradelite.service.SymbolManagementService.AddResult;

@ExtendWith(MockitoExtension.class)
class SymbolManagementServiceTest {

    @Mock private SymbolRegistry symbolRegistry;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private FinnhubClient finnhubClient;
    @Mock private CoinGeckoClient coinGeckoClient;
    @Mock private YahooFinanceClient yahooFinanceClient;
    @Mock private NewlyAddedSymbolRepository newlyAddedSymbolRepository;

    private SymbolManagementService service;

    private static PriceQuoteResponse validQuote() {
        PriceQuoteResponse quote = new PriceQuoteResponse();
        quote.setStockSymbol(new StockSymbol("X", "X"));
        quote.setCurrentPrice(150.0);
        return quote;
    }

    @BeforeEach
    void setUp() {
        service =
                new SymbolManagementService(
                        symbolRegistry,
                        targetPriceProvider,
                        finnhubClient,
                        coinGeckoClient,
                        yahooFinanceClient,
                        newlyAddedSymbolRepository);
    }

    @Test
    void addSymbol_domesticStock_happyPath() {
        when(symbolRegistry.isInternationalSymbol("AAPL")).thenReturn(false);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class)))
                .thenReturn(Optional.of(validQuote()));
        when(symbolRegistry.addSymbol("AAPL", "Apple Inc")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), eq(AssetType.STOCK)))
                .thenReturn(true);

        AddResult result = service.addSymbol("AAPL", "Apple Inc", 140.0, 160.0);

        assertThat(result.success(), is(true));
        assertThat(result.message(), containsString("Added Apple Inc (AAPL)"));
        verify(symbolRegistry).addSymbol("AAPL", "Apple Inc");
        verify(targetPriceProvider).addTargetPrice(any(TargetPrice.class), eq(AssetType.STOCK));
        verify(newlyAddedSymbolRepository).insert(eq("AAPL"), anyLong());
    }

    @Test
    void addSymbol_nullTargets_defaultsToZero() {
        when(symbolRegistry.isInternationalSymbol("AAPL")).thenReturn(false);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class)))
                .thenReturn(Optional.of(validQuote()));
        when(symbolRegistry.addSymbol(anyString(), anyString())).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), eq(AssetType.STOCK)))
                .thenReturn(true);

        AddResult result = service.addSymbol("AAPL", "Apple Inc", null, null);

        assertThat(result.success(), is(true));
    }

    @Test
    void addSymbol_internationalStock_happyPath() {
        OhlcvRecord record =
                new OhlcvRecord("SAP.DE", LocalDate.now(), 100.0, 110.0, 95.0, 105.0, 1_000L);
        when(symbolRegistry.isInternationalSymbol("SAP.DE")).thenReturn(true);
        when(yahooFinanceClient.fetchDailyOhlcv("SAP.DE", 5)).thenReturn(List.of(record));
        when(symbolRegistry.addSymbol("SAP.DE", "SAP SE")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), eq(AssetType.STOCK)))
                .thenReturn(true);

        AddResult result = service.addSymbol("SAP.DE", "SAP SE", 150.0, 200.0);

        assertThat(result.success(), is(true));
        assertThat(result.message(), containsString("Added SAP SE (SAP.DE)"));
    }

    @Test
    void addSymbol_crypto_happyPath() {
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(50000.0);
        when(symbolRegistry.isInternationalSymbol("BITCOIN")).thenReturn(false);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.empty());
        when(coinGeckoClient.getCoinPriceData(any())).thenReturn(coinData);
        when(symbolRegistry.addSymbol("BITCOIN", "Bitcoin")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), eq(AssetType.STOCK)))
                .thenReturn(true);

        AddResult result = service.addSymbol("BITCOIN", "Bitcoin", null, null);

        assertThat(result.success(), is(true));
    }

    @Test
    void addSymbol_invalidTicker_returnsFailure_domesticSource() {
        when(symbolRegistry.isInternationalSymbol("GHOST")).thenReturn(false);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class))).thenReturn(Optional.empty());

        AddResult result = service.addSymbol("GHOST", "Ghost", null, null);

        assertThat(result.success(), is(false));
        assertThat(result.message(), containsString("Finnhub or CoinGecko"));
        verify(symbolRegistry, never()).addSymbol(anyString(), anyString());
    }

    @Test
    void addSymbol_invalidTicker_returnsFailure_internationalSource() {
        when(symbolRegistry.isInternationalSymbol("BOGUS.DE")).thenReturn(true);
        when(yahooFinanceClient.fetchDailyOhlcv("BOGUS.DE", 5)).thenReturn(List.of());

        AddResult result = service.addSymbol("BOGUS.DE", "Bogus", null, null);

        assertThat(result.success(), is(false));
        assertThat(result.message(), containsString("Yahoo Finance"));
        verify(symbolRegistry, never()).addSymbol(anyString(), anyString());
    }

    @Test
    void addSymbol_duplicate_returnsFailure() {
        when(symbolRegistry.isInternationalSymbol("AAPL")).thenReturn(false);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class)))
                .thenReturn(Optional.of(validQuote()));
        when(symbolRegistry.addSymbol("AAPL", "Apple Inc")).thenReturn(false);

        AddResult result = service.addSymbol("AAPL", "Apple Inc", null, null);

        assertThat(result.success(), is(false));
        assertThat(result.message(), containsString("already exist"));
        verify(targetPriceProvider, never())
                .addTargetPrice(any(TargetPrice.class), any(AssetType.class));
    }

    @Test
    void addSymbol_targetPriceAddFails_rollsBackSymbol() {
        when(symbolRegistry.isInternationalSymbol("AAPL")).thenReturn(false);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class)))
                .thenReturn(Optional.of(validQuote()));
        when(symbolRegistry.addSymbol("AAPL", "Apple Inc")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), eq(AssetType.STOCK)))
                .thenReturn(false);

        AddResult result = service.addSymbol("AAPL", "Apple Inc", null, null);

        assertThat(result.success(), is(false));
        assertThat(result.message(), containsString("Failed to add target prices"));
        verify(symbolRegistry).removeSymbol("AAPL");
    }

    @Test
    void addSymbol_backfillQueueFails_stillReturnsSuccess() {
        when(symbolRegistry.isInternationalSymbol("AAPL")).thenReturn(false);
        when(finnhubClient.tryGetPriceQuote(any(StockSymbol.class)))
                .thenReturn(Optional.of(validQuote()));
        when(symbolRegistry.addSymbol("AAPL", "Apple Inc")).thenReturn(true);
        when(targetPriceProvider.addTargetPrice(any(TargetPrice.class), eq(AssetType.STOCK)))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(newlyAddedSymbolRepository)
                .insert(anyString(), anyLong());

        AddResult result = service.addSymbol("AAPL", "Apple Inc", null, null);

        assertThat(result.success(), is(true));
    }

    @Test
    void removeSymbol_existing_returnsTrue() {
        when(symbolRegistry.removeSymbol("AAPL")).thenReturn(true);

        boolean removed = service.removeSymbol("AAPL");

        assertThat(removed, is(true));
        verify(targetPriceProvider).removeSymbolFromTargetPrices("AAPL", AssetType.STOCK);
    }

    @Test
    void removeSymbol_notFound_returnsFalse() {
        when(symbolRegistry.removeSymbol("GHOST")).thenReturn(false);

        boolean removed = service.removeSymbol("GHOST");

        assertThat(removed, is(false));
        verify(targetPriceProvider, never())
                .removeSymbolFromTargetPrices(anyString(), any(AssetType.class));
    }
}
