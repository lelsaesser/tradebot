package org.tradelite.core;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.common.TickerSymbol;

@ExtendWith(MockitoExtension.class)
class BasePriceEvaluatorTest {

    @Mock private TelegramClient telegramClient;

    @Mock private TargetPriceProvider targetPriceProvider;

    @InjectMocks private TestPriceEvaluator priceEvaluator;

    private TickerSymbol stockSymbol;

    @BeforeEach
    void setUp() {
        stockSymbol = StockSymbol.AAPL;
    }

    static class TestPriceEvaluator extends BasePriceEvaluator {
        public TestPriceEvaluator(
                TelegramClient telegramClient, TargetPriceProvider targetPriceProvider) {
            super(telegramClient, targetPriceProvider);
        }

        @Override
        public int evaluatePrice() {
            return 0;
        }
    }

    @Test
    void testComparePrices_sellAlert() {
        when(targetPriceProvider.isSymbolIgnored(stockSymbol, IgnoreReason.SELL_ALERT))
                .thenReturn(false);

        priceEvaluator.comparePrices(stockSymbol, 200, 100, 150);

        verify(telegramClient).sendMessage(contains(((StockSymbol) stockSymbol).getDisplayName()));
        verify(targetPriceProvider).addIgnoredSymbol(stockSymbol, IgnoreReason.SELL_ALERT);
    }

    @Test
    void testComparePrices_buyAlert() {
        when(targetPriceProvider.isSymbolIgnored(stockSymbol, IgnoreReason.BUY_ALERT))
                .thenReturn(false);

        priceEvaluator.comparePrices(stockSymbol, 50, 100, 200);

        verify(telegramClient).sendMessage(contains(((StockSymbol) stockSymbol).getDisplayName()));
        verify(targetPriceProvider).addIgnoredSymbol(stockSymbol, IgnoreReason.BUY_ALERT);
    }

    @Test
    void testComparePrices_sellAlert_ignored() {
        when(targetPriceProvider.isSymbolIgnored(stockSymbol, IgnoreReason.SELL_ALERT))
                .thenReturn(true);

        priceEvaluator.comparePrices(stockSymbol, 200, 100, 150);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void testComparePrices_buyAlert_ignored() {
        when(targetPriceProvider.isSymbolIgnored(stockSymbol, IgnoreReason.BUY_ALERT))
                .thenReturn(true);

        priceEvaluator.comparePrices(stockSymbol, 50, 100, 200);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void testComparePrices_noAlert() {
        priceEvaluator.comparePrices(stockSymbol, 120, 100, 150);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }
}
