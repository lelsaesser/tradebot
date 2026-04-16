package org.tradelite.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;

@ExtendWith(MockitoExtension.class)
class EvaluatorLivePriceSourceTest {

    @Mock private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @Mock private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    private EvaluatorLivePriceSource livePriceSource;

    private final Map<String, Double> finnhubCache = new ConcurrentHashMap<>();
    private final Map<CoinId, Double> coinGeckoCache = new EnumMap<>(CoinId.class);

    @BeforeEach
    void setUp() {
        finnhubCache.clear();
        coinGeckoCache.clear();
        lenient().when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(finnhubCache);
        lenient().when(coinGeckoPriceEvaluator.getLastPriceCache()).thenReturn(coinGeckoCache);
        livePriceSource = new EvaluatorLivePriceSource(finnhubPriceEvaluator, coinGeckoPriceEvaluator);
    }

    @Test
    void getPrice_stockSymbol_dispatchesToFinnhubCache() {
        finnhubCache.put("AAPL", 150.25);
        StockSymbol apple = new StockSymbol("AAPL", "Apple");

        Optional<Double> result = livePriceSource.getPrice(apple);

        assertTrue(result.isPresent());
        assertEquals(150.25, result.get());
    }

    @Test
    void getPrice_stockSymbol_notInCache_returnsEmpty() {
        StockSymbol unknown = new StockSymbol("XYZ", "Unknown Corp");

        Optional<Double> result = livePriceSource.getPrice(unknown);

        assertTrue(result.isEmpty());
    }

    @Test
    void getPrice_cryptoSymbol_dispatchesToCoinGeckoCache() {
        coinGeckoCache.put(CoinId.BITCOIN, 52000.0);

        Optional<Double> result = livePriceSource.getPrice(CoinId.BITCOIN);

        assertTrue(result.isPresent());
        assertEquals(52000.0, result.get());
    }

    @Test
    void getPrice_cryptoSymbol_notInCache_returnsEmpty() {
        Optional<Double> result = livePriceSource.getPrice(CoinId.ETHEREUM);

        assertTrue(result.isEmpty());
    }

    @Test
    void getPriceByKey_stockKey_findsInFinnhubCache() {
        finnhubCache.put("MSFT", 420.0);

        Optional<Double> result = livePriceSource.getPriceByKey("MSFT");

        assertTrue(result.isPresent());
        assertEquals(420.0, result.get());
    }

    @Test
    void getPriceByKey_cryptoKey_fallsBackToCoinGecko() {
        coinGeckoCache.put(CoinId.BITCOIN, 52000.0);

        Optional<Double> result = livePriceSource.getPriceByKey("bitcoin");

        assertTrue(result.isPresent());
        assertEquals(52000.0, result.get());
    }

    @Test
    void getPriceByKey_unknownKey_returnsEmpty() {
        Optional<Double> result = livePriceSource.getPriceByKey("unknown_symbol");

        assertTrue(result.isEmpty());
    }

    @Test
    void getPriceByKey_stockKeyTakesPrecedenceOverCrypto() {
        finnhubCache.put("solana", 99.0);
        coinGeckoCache.put(CoinId.SOLANA, 180.0);

        Optional<Double> result = livePriceSource.getPriceByKey("solana");

        assertTrue(result.isPresent());
        assertEquals(99.0, result.get(), "Finnhub cache should take precedence");
    }
}
