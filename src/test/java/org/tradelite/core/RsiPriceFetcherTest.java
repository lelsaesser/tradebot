package org.tradelite.core;

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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RsiPriceFetcherTest {

    @Mock
    private FinnhubClient finnhubClient;

    @Mock
    private CoinGeckoClient coinGeckoClient;

    @Mock
    private TargetPriceProvider targetPriceProvider;

    @Mock
    private RsiService rsiService;

    @InjectMocks
    private RsiPriceFetcher rsiPriceFetcher;

    @Test
    void testFetchStockClosingPrices() {
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of(new TargetPrice("AAPL", 100, 200)));
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenReturn(new PriceQuoteResponse());

        rsiPriceFetcher.fetchStockClosingPrices();

        verify(rsiService, times(1)).addPrice(any(StockSymbol.class), anyDouble(), any());
    }

    @Test
    void testFetchStockClosingPrices_exception() {
        when(targetPriceProvider.getStockTargetPrices()).thenReturn(List.of(new TargetPrice("AAPL", 100, 200)));
        when(finnhubClient.getPriceQuote(any(StockSymbol.class))).thenThrow(new RuntimeException("API error"));

        rsiPriceFetcher.fetchStockClosingPrices();

        verify(rsiService, never()).addPrice(any(StockSymbol.class), anyDouble(), any());
    }

    @Test
    void testFetchCryptoClosingPrices() {
        when(targetPriceProvider.getCoinTargetPrices()).thenReturn(List.of(new TargetPrice("bitcoin", 100, 200)));
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(50000);
        when(coinGeckoClient.getCoinPriceData(any(CoinId.class))).thenReturn(coinData);

        rsiPriceFetcher.fetchCryptoClosingPrices();

        verify(rsiService, times(1)).addPrice(any(CoinId.class), anyDouble(), any());
    }

    @Test
    void testFetchCryptoClosingPrices_coinNotFound() {
        when(targetPriceProvider.getCoinTargetPrices()).thenReturn(List.of(new TargetPrice("not_a_coin", 100, 200)));

        rsiPriceFetcher.fetchCryptoClosingPrices();

        verify(coinGeckoClient, never()).getCoinPriceData(any(CoinId.class));
        verify(rsiService, never()).addPrice(any(CoinId.class), anyDouble(), any());
    }
}
