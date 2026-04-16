package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.coingecko.CoinGeckoClient;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.common.TargetPriceProvider;

/**
 * RsiPriceFetcher methods are now no-ops (deprecated). These tests verify they don't throw. The
 * class will be deleted in a follow-up subtask.
 */
@ExtendWith(MockitoExtension.class)
class RsiPriceFetcherTest {

    @Mock private FinnhubClient finnhubClient;
    @Mock private CoinGeckoClient coinGeckoClient;
    @Mock private TargetPriceProvider targetPriceProvider;
    @Mock private org.tradelite.common.SymbolRegistry symbolRegistry;

    @InjectMocks private RsiPriceFetcher rsiPriceFetcher;

    @Test
    void fetchStockClosingPrices_isNoOp() {
        assertDoesNotThrow(() -> rsiPriceFetcher.fetchStockClosingPrices());
    }

    @Test
    void fetchCryptoClosingPrices_isNoOp() {
        assertDoesNotThrow(() -> rsiPriceFetcher.fetchCryptoClosingPrices());
    }
}
