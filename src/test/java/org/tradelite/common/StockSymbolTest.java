package org.tradelite.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StockSymbolTest {

    @Test
    void constructor_setsValues() {
        StockSymbol symbol = new StockSymbol("AAPL", "Apple Inc");

        assertThat(symbol.getTicker(), is("AAPL"));
        assertThat(symbol.getCompanyName(), is("Apple Inc"));
    }

    @Test
    void getTicker_returnsCorrectValue() {
        StockSymbol symbol = new StockSymbol("MSFT", "Microsoft");

        assertThat(symbol.getTicker(), is("MSFT"));
    }

    @Test
    void getCompanyName_returnsCorrectValue() {
        StockSymbol symbol = new StockSymbol("GOOGL", "Alphabet Inc");

        assertThat(symbol.getCompanyName(), is("Alphabet Inc"));
    }

    @Test
    void equals_sameTickerAndName_returnsTrue() {
        StockSymbol symbol1 = new StockSymbol("AAPL", "Apple Inc");
        StockSymbol symbol2 = new StockSymbol("AAPL", "Apple Inc");

        assertEquals(symbol1, symbol2);
    }

    @Test
    void equals_differentTicker_returnsFalse() {
        StockSymbol symbol1 = new StockSymbol("AAPL", "Apple Inc");
        StockSymbol symbol2 = new StockSymbol("MSFT", "Microsoft");

        assertNotEquals(symbol1, symbol2);
    }

    @Test
    void equals_differentName_returnsTrue() {
        // StockSymbol equals() only compares ticker, not companyName
        StockSymbol symbol1 = new StockSymbol("AAPL", "Apple Inc");
        StockSymbol symbol2 = new StockSymbol("AAPL", "Apple");

        assertEquals(symbol1, symbol2);
    }

    @Test
    void equals_null_returnsFalse() {
        StockSymbol symbol = new StockSymbol("AAPL", "Apple Inc");

        assertNotEquals(null, symbol);
    }

    @Test
    void equals_differentClass_returnsFalse() {
        StockSymbol symbol = new StockSymbol("AAPL", "Apple Inc");

        assertNotEquals("AAPL", symbol);
    }

    @Test
    void equals_sameObject_returnsTrue() {
        StockSymbol symbol = new StockSymbol("AAPL", "Apple Inc");

        assertEquals(symbol, symbol);
    }

    @Test
    void hashCode_sameValues_returnsSameHash() {
        StockSymbol symbol1 = new StockSymbol("AAPL", "Apple Inc");
        StockSymbol symbol2 = new StockSymbol("AAPL", "Apple Inc");

        assertEquals(symbol1.hashCode(), symbol2.hashCode());
    }

    @Test
    void hashCode_sameTicker_returnsSameHash() {
        // hashCode only depends on ticker, not companyName
        StockSymbol symbol1 = new StockSymbol("AAPL", "Apple Inc");
        StockSymbol symbol2 = new StockSymbol("AAPL", "Apple");

        assertEquals(symbol1.hashCode(), symbol2.hashCode());
    }

    @Test
    void hashCode_differentTicker_returnsDifferentHash() {
        StockSymbol symbol1 = new StockSymbol("AAPL", "Apple Inc");
        StockSymbol symbol2 = new StockSymbol("MSFT", "Microsoft");

        assertNotEquals(symbol1.hashCode(), symbol2.hashCode());
    }

    @Test
    void toString_returnsTicker() {
        StockSymbol symbol = new StockSymbol("AAPL", "Apple Inc");

        String result = symbol.toString();

        assertThat(result, is("AAPL"));
    }

    @Test
    void getDisplayName_returnsFormattedString() {
        StockSymbol symbol = new StockSymbol("AAPL", "Apple Inc");

        String result = symbol.getDisplayName();

        assertThat(result, is("Apple Inc (AAPL)"));
    }

    @Test
    void getName_returnsTicker() {
        StockSymbol symbol = new StockSymbol("MSFT", "Microsoft");

        assertThat(symbol.getName(), is("MSFT"));
    }

    @Test
    void getSymbolType_returnsStock() {
        StockSymbol symbol = new StockSymbol("AAPL", "Apple Inc");

        assertThat(symbol.getSymbolType(), is(SymbolType.STOCK));
    }
}
