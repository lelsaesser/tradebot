package org.tradelite.client.finnhub;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.tradelite.common.StockSymbol;

class NoFallbackFinnhubStrategyTest {

    @Test
    void onQuoteFailure_throwsOriginalRuntimeException() {
        NoFallbackFinnhubStrategy strategy = new NoFallbackFinnhubStrategy();
        RuntimeException cause = new RuntimeException("fail");

        assertThrows(
                RuntimeException.class,
                () -> strategy.onQuoteFailure(new StockSymbol("META", "Meta"), cause));
    }

    @Test
    void onInsiderFailure_wrapsCheckedException() {
        NoFallbackFinnhubStrategy strategy = new NoFallbackFinnhubStrategy();

        assertThrows(
                IllegalStateException.class,
                () -> strategy.onInsiderFailure(new StockSymbol("META", "Meta"), new Exception("x")));
    }
}
