package org.tradelite.core;

import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.common.TickerSymbol;
import org.tradelite.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class BasePriceEvaluatorTest {

    @Mock private NotificationService notificationService;

    @Mock private TargetPriceProvider targetPriceProvider;

    @InjectMocks private TestPriceEvaluator priceEvaluator;

    private TickerSymbol stockSymbol;

    @BeforeEach
    void setUp() {
        stockSymbol = StockSymbol.AAPL;
    }

    static class TestPriceEvaluator extends BasePriceEvaluator {
        public TestPriceEvaluator(
                NotificationService notificationService, TargetPriceProvider targetPriceProvider) {
            super(notificationService, targetPriceProvider);
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

        verify(notificationService)
                .sendNotification(contains(((StockSymbol) stockSymbol).getDisplayName()));
        verify(targetPriceProvider).addIgnoredSymbol(stockSymbol, IgnoreReason.SELL_ALERT);
    }

    @Test
    void testComparePrices_buyAlert() {
        when(targetPriceProvider.isSymbolIgnored(stockSymbol, IgnoreReason.BUY_ALERT))
                .thenReturn(false);

        priceEvaluator.comparePrices(stockSymbol, 50, 100, 200);

        verify(notificationService)
                .sendNotification(contains(((StockSymbol) stockSymbol).getDisplayName()));
        verify(targetPriceProvider).addIgnoredSymbol(stockSymbol, IgnoreReason.BUY_ALERT);
    }

    @Test
    void testComparePrices_sellAlert_ignored() {
        when(targetPriceProvider.isSymbolIgnored(stockSymbol, IgnoreReason.SELL_ALERT))
                .thenReturn(true);

        priceEvaluator.comparePrices(stockSymbol, 200, 100, 150);

        verify(notificationService, never()).sendNotification(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void testComparePrices_buyAlert_ignored() {
        when(targetPriceProvider.isSymbolIgnored(stockSymbol, IgnoreReason.BUY_ALERT))
                .thenReturn(true);

        priceEvaluator.comparePrices(stockSymbol, 50, 100, 200);

        verify(notificationService, never()).sendNotification(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void testComparePrices_noAlert() {
        priceEvaluator.comparePrices(stockSymbol, 120, 100, 150);

        verify(notificationService, never()).sendNotification(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }
}
