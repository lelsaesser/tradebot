package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.RsiService;

@ExtendWith(MockitoExtension.class)
class RsiPriceFetcherTest {

    @Mock private FinnhubClient finnhubClient;

    @Mock private CoinGeckoClient coinGeckoClient;

    @Mock private TargetPriceProvider targetPriceProvider;

    @Mock private RsiService rsiService;

    @Mock private org.tradelite.service.StockSymbolRegistry stockSymbolRegistry;

    @InjectMocks private RsiPriceFetcher rsiPriceFetcher;

    @Test
    void testFetchStockClosingPrices() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("AAPL", 100, 200)));
        when(stockSymbolRegistry.fromString("AAPL"))
                .thenReturn(java.util.Optional.of(new StockSymbol("AAPL", "Apple")));
        when(finnhubClient.getPriceQuote(any(StockSymbol.class)))
                .thenReturn(new PriceQuoteResponse());

        rsiPriceFetcher.fetchStockClosingPrices();

        verify(rsiService, times(1)).addPrice(any(StockSymbol.class), anyDouble(), any());
    }

    @Test
    void testFetchStockClosingPrices_exception() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("AAPL", 100, 200)));
        when(stockSymbolRegistry.fromString("AAPL"))
                .thenReturn(java.util.Optional.of(new StockSymbol("AAPL", "Apple")));
        when(finnhubClient.getPriceQuote(any(StockSymbol.class)))
                .thenThrow(new RuntimeException("API error"));

        assertThrows(RuntimeException.class, () -> rsiPriceFetcher.fetchStockClosingPrices());

        verify(rsiService, never()).addPrice(any(StockSymbol.class), anyDouble(), any());
    }

    @Test
    void testFetchCryptoClosingPrices() throws IOException {
        when(targetPriceProvider.getCoinTargetPrices())
                .thenReturn(List.of(new TargetPrice("bitcoin", 100, 200)));
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(50000);
        when(coinGeckoClient.getCoinPriceData(any(CoinId.class))).thenReturn(coinData);

        rsiPriceFetcher.fetchCryptoClosingPrices();

        verify(rsiService, times(1)).addPrice(any(CoinId.class), anyDouble(), any());
    }

    @Test
    void testFetchCryptoClosingPrices_coinNotFound() throws IOException {
        when(targetPriceProvider.getCoinTargetPrices())
                .thenReturn(List.of(new TargetPrice("not_a_coin", 100, 200)));

        rsiPriceFetcher.fetchCryptoClosingPrices();

        verify(coinGeckoClient, never()).getCoinPriceData(any(CoinId.class));
        verify(rsiService, never()).addPrice(any(CoinId.class), anyDouble(), any());
    }

    @Test
    void testFetchStockClosingPrices_invalidSymbol() throws IOException {
        when(targetPriceProvider.getStockTargetPrices())
                .thenReturn(List.of(new TargetPrice("INVALID_SYMBOL", 100, 200)));
        when(stockSymbolRegistry.fromString("INVALID_SYMBOL"))
                .thenReturn(java.util.Optional.empty());

        rsiPriceFetcher.fetchStockClosingPrices();

        verify(finnhubClient, never()).getPriceQuote(any(StockSymbol.class));
        verify(rsiService, never()).addPrice(any(StockSymbol.class), anyDouble(), any());
    }

    @Test
    void testFetchCryptoClosingPrices_exception() throws IOException {
        when(targetPriceProvider.getCoinTargetPrices())
                .thenReturn(List.of(new TargetPrice("bitcoin", 100, 200)));
        when(coinGeckoClient.getCoinPriceData(any(CoinId.class)))
                .thenThrow(new RuntimeException("Network error"));

        assertThrows(RuntimeException.class, () -> rsiPriceFetcher.fetchCryptoClosingPrices());

        verify(rsiService, never()).addPrice(any(CoinId.class), anyDouble(), any());
    }
}
