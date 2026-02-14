package org.tradelite.client.coingecko;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.tradelite.common.CoinId;

class NoFallbackCoinGeckoStrategyTest {

    @Test
    void onPriceFailure_throwsOriginalRuntimeException() {
        NoFallbackCoinGeckoStrategy strategy = new NoFallbackCoinGeckoStrategy();

        assertThrows(
                RuntimeException.class,
                () -> strategy.onPriceFailure(CoinId.BITCOIN, new RuntimeException("fail")));
    }

    @Test
    void onPriceFailure_wrapsCheckedException() {
        NoFallbackCoinGeckoStrategy strategy = new NoFallbackCoinGeckoStrategy();

        assertThrows(
                IllegalStateException.class,
                () -> strategy.onPriceFailure(CoinId.BITCOIN, new Exception("fail")));
    }
}
