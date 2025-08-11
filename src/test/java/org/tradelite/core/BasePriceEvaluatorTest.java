package org.tradelite.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.common.TickerSymbol;
import org.tradelite.common.SymbolType;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BasePriceEvaluatorTest {

    @Mock
    private TelegramClient telegramClient;

    @Mock
    private TargetPriceProvider targetPriceProvider;

    @InjectMocks
    private TestPriceEvaluator priceEvaluator;

    static class TestPriceEvaluator extends BasePriceEvaluator {
        public TestPriceEvaluator(TelegramClient telegramClient, TargetPriceProvider targetPriceProvider) {
            super(telegramClient, targetPriceProvider);
        }

        @Override
        public int evaluatePrice() {
            return 0;
        }
    }

    private static class TestTickerSymbol implements TickerSymbol {
        public String getSymbol() {
            return "TEST";
        }

        @Override
        public String getName() {
            return "Test Symbol";
        }

        @Override
        public SymbolType getSymbolType() {
            return SymbolType.STOCK;
        }
    }

    @Test
    void testComparePrices_sellAlert() {
        TickerSymbol ticker = new TestTickerSymbol();
        when(targetPriceProvider.isSymbolIgnored(ticker, IgnoreReason.SELL_ALERT)).thenReturn(false);

        priceEvaluator.comparePrices(ticker, 200, 100, 150);

        verify(telegramClient).sendMessage(anyString());
        verify(targetPriceProvider).addIgnoredSymbol(ticker, IgnoreReason.SELL_ALERT);
    }

    @Test
    void testComparePrices_buyAlert() {
        TickerSymbol ticker = new TestTickerSymbol();
        when(targetPriceProvider.isSymbolIgnored(ticker, IgnoreReason.BUY_ALERT)).thenReturn(false);

        priceEvaluator.comparePrices(ticker, 50, 100, 200);

        verify(telegramClient).sendMessage(anyString());
        verify(targetPriceProvider).addIgnoredSymbol(ticker, IgnoreReason.BUY_ALERT);
    }

    @Test
    void testComparePrices_sellAlert_ignored() {
        TickerSymbol ticker = new TestTickerSymbol();
        when(targetPriceProvider.isSymbolIgnored(ticker, IgnoreReason.SELL_ALERT)).thenReturn(true);

        priceEvaluator.comparePrices(ticker, 200, 100, 150);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void testComparePrices_buyAlert_ignored() {
        TickerSymbol ticker = new TestTickerSymbol();
        when(targetPriceProvider.isSymbolIgnored(ticker, IgnoreReason.BUY_ALERT)).thenReturn(true);

        priceEvaluator.comparePrices(ticker, 50, 100, 200);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }

    @Test
    void testComparePrices_noAlert() {
        TickerSymbol ticker = new TestTickerSymbol();

        priceEvaluator.comparePrices(ticker, 120, 100, 150);

        verify(telegramClient, never()).sendMessage(anyString());
        verify(targetPriceProvider, never()).addIgnoredSymbol(any(), any());
    }
}
