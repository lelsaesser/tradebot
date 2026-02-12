package org.tradelite.core;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.CoinId;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

@ExtendWith(MockitoExtension.class)
class CoinGeckoPriceEvaluatorTest {

    @Mock private CoinGeckoClient coinGeckoClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private TelegramGateway telegramClient;

    private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    @BeforeEach
    void setUp() {
        coinGeckoPriceEvaluator =
                spy(
                        new CoinGeckoPriceEvaluator(
                                coinGeckoClient, targetPriceProvider, telegramClient));
    }

    @Test
    void evaluatePrice_priceDidNotChange() throws InterruptedException {
        double lastPrice = Math.random();

        for (CoinId coinId : CoinId.getAll()) {
            coinGeckoPriceEvaluator.lastPriceCache.put(coinId, lastPrice);
        }

        List<TargetPrice> targetPrices = new ArrayList<>();
        for (CoinId coinId : CoinId.getAll()) {
            targetPrices.add(new TargetPrice(coinId.getName(), lastPrice - 1000, lastPrice + 1000));
        }
        when(targetPriceProvider.getCoinTargetPrices()).thenReturn(targetPrices);

        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(lastPrice);

        when(coinGeckoClient.getCoinPriceData(any())).thenReturn(coinData);

        int coinDataSize = coinGeckoPriceEvaluator.evaluatePrice();

        verify(targetPriceProvider, times(1)).getCoinTargetPrices();
        verify(coinGeckoClient, times(CoinId.getAll().size())).getCoinPriceData(any());

        assertThat(coinGeckoPriceEvaluator.lastPriceCache, aMapWithSize(CoinId.getAll().size()));
        assertThat(coinDataSize, is(0));
    }

    @Test
    void evaluatePrice_happyPath() throws InterruptedException {
        for (CoinId coinId : CoinId.getAll()) {
            coinGeckoPriceEvaluator.lastPriceCache.put(coinId, null);
        }
        coinGeckoPriceEvaluator.lastPriceCache.remove(CoinId.BITCOIN);

        List<TargetPrice> targetPrices = new ArrayList<>();
        targetPrices.add(new TargetPrice(CoinId.BITCOIN.getName(), 1000, 2000));

        when(targetPriceProvider.getCoinTargetPrices()).thenReturn(targetPrices);

        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(1500);
        coinData.setCoinId(CoinId.BITCOIN);

        when(coinGeckoClient.getCoinPriceData(CoinId.BITCOIN)).thenReturn(coinData);

        int coinDataSize = coinGeckoPriceEvaluator.evaluatePrice();

        assertThat(coinDataSize, is(1));
        verify(coinGeckoClient, times(1)).getCoinPriceData(CoinId.BITCOIN);
        verify(coinGeckoPriceEvaluator, times(1))
                .comparePrices(coinData.getCoinId(), coinData.getUsd(), 1000, 2000);
    }

    @Test
    void evaluateHighPriceChange_shouldSendMessageWhenPriceSwingExceedsThreshold() {
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setCoinId(CoinId.BITCOIN);
        coinData.setUsd_24h_change(6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        CoinId.BITCOIN, IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(false);

        coinGeckoPriceEvaluator.evaluateHighPriceChange(coinData);

        verify(telegramClient, times(1)).sendMessage(anyString());
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.CHANGE_PERCENT_ALERT, 5);
    }

    @Test
    void evaluateHighPriceChange_shouldNotSendMessageWhenPriceSwingIsBelowThreshold() {
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setCoinId(CoinId.BITCOIN);
        coinData.setUsd_24h_change(4.0);

        coinGeckoPriceEvaluator.evaluateHighPriceChange(coinData);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void evaluateHighPriceChange_shouldNotSendMessageWhenSymbolIsIgnored() {
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setCoinId(CoinId.BITCOIN);
        coinData.setUsd_24h_change(6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        CoinId.BITCOIN, IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(true);

        coinGeckoPriceEvaluator.evaluateHighPriceChange(coinData);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never())
                .addIgnoredSymbol(any(CoinId.class), any(IgnoreReason.class), anyInt());
    }

    @Test
    void evaluatePrice_nullCoinData() throws InterruptedException {
        when(coinGeckoClient.getCoinPriceData(any())).thenReturn(null);

        int coinDataSize = coinGeckoPriceEvaluator.evaluatePrice();

        assertThat(coinDataSize, is(0));
    }

    @Test
    void evaluateHighPriceChange_negativeChange() {
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setCoinId(CoinId.BITCOIN);
        coinData.setUsd_24h_change(-6.0);

        when(targetPriceProvider.isSymbolIgnored(
                        CoinId.BITCOIN, IgnoreReason.CHANGE_PERCENT_ALERT, 5))
                .thenReturn(false);

        coinGeckoPriceEvaluator.evaluateHighPriceChange(coinData);

        verify(telegramClient, times(1)).sendMessage(anyString());
        verify(targetPriceProvider, times(1))
                .addIgnoredSymbol(CoinId.BITCOIN, IgnoreReason.CHANGE_PERCENT_ALERT, 5);
    }

    @Test
    void evaluatePrice_lastPriceNotNull() throws InterruptedException {
        coinGeckoPriceEvaluator.lastPriceCache.put(CoinId.BITCOIN, 1500.0);

        List<TargetPrice> targetPrices = new ArrayList<>();
        targetPrices.add(new TargetPrice(CoinId.BITCOIN.getName(), 1000, 2000));

        when(targetPriceProvider.getCoinTargetPrices()).thenReturn(targetPrices);

        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(1500.00001);
        coinData.setCoinId(CoinId.BITCOIN);

        when(coinGeckoClient.getCoinPriceData(CoinId.BITCOIN)).thenReturn(coinData);

        int coinDataSize = coinGeckoPriceEvaluator.evaluatePrice();

        assertThat(coinDataSize, is(0));
        verify(coinGeckoClient, times(CoinId.getAll().size())).getCoinPriceData(any());
    }

    @Test
    void evaluatePrice_lastPriceNull() throws InterruptedException {
        coinGeckoPriceEvaluator.lastPriceCache.put(CoinId.BITCOIN, null);

        List<TargetPrice> targetPrices = new ArrayList<>();
        targetPrices.add(new TargetPrice(CoinId.BITCOIN.getName(), 1000, 2000));

        when(targetPriceProvider.getCoinTargetPrices()).thenReturn(targetPrices);

        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(1500.0);
        coinData.setCoinId(CoinId.BITCOIN);

        when(coinGeckoClient.getCoinPriceData(CoinId.BITCOIN)).thenReturn(coinData);

        int coinDataSize = coinGeckoPriceEvaluator.evaluatePrice();

        assertThat(coinDataSize, is(1));
        verify(coinGeckoClient, times(1)).getCoinPriceData(CoinId.BITCOIN);
        verify(coinGeckoPriceEvaluator, times(1))
                .comparePrices(coinData.getCoinId(), coinData.getUsd(), 1000, 2000);
    }
}
