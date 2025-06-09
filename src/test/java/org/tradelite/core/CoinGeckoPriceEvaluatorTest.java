package org.tradelite.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoinGeckoPriceEvaluatorTest {

    @Mock
    private CoinGeckoClient coinGeckoClient;
    @Mock
    private TargetPriceProvider targetPriceProvider;

    private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    @BeforeEach
    void setUp() {
        coinGeckoPriceEvaluator = spy(new CoinGeckoPriceEvaluator(coinGeckoClient, targetPriceProvider, null));
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
        verify(coinGeckoPriceEvaluator, times(1)).comparePrices(coinData.getCoinId(), coinData.getUsd(), 1000, 2000);
    }
}
