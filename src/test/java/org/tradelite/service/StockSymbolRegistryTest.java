package org.tradelite.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.common.StockSymbol;

class StockSymbolRegistryTest {

    private StockSymbolRegistry stockSymbolRegistry;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        stockSymbolRegistry = new StockSymbolRegistry(objectMapper);
    }

    @Test
    void fromString_existingSymbol_returnsStockSymbol() {
        Optional<StockSymbol> result = stockSymbolRegistry.fromString("AAPL");

        assertTrue(result.isPresent());
        assertThat(result.get().getTicker(), is("AAPL"));
        assertThat(result.get().getCompanyName(), is("Apple"));
    }

    @Test
    void fromString_nonExistingSymbol_returnsEmpty() {
        Optional<StockSymbol> result = stockSymbolRegistry.fromString("INVALID");

        assertTrue(result.isEmpty());
    }

    @Test
    void fromString_nullOrEmpty_returnsEmpty() {
        assertTrue(stockSymbolRegistry.fromString(null).isEmpty());
        assertTrue(stockSymbolRegistry.fromString("").isEmpty());
    }

    @Test
    void getAll_returnsAllSymbols() {
        List<StockSymbol> result = stockSymbolRegistry.getAll();

        assertFalse(result.isEmpty());
        assertTrue(result.stream().anyMatch(s -> s.getTicker().equals("AAPL")));
    }

    @Test
    void addSymbol_newSymbol_returnsTrue() {
        boolean result = stockSymbolRegistry.addSymbol("TEST", "Test Company");

        assertTrue(result);
        Optional<StockSymbol> added = stockSymbolRegistry.fromString("TEST");
        assertTrue(added.isPresent());
        assertThat(added.get().getTicker(), is("TEST"));
        assertThat(added.get().getCompanyName(), is("Test Company"));

        // Cleanup
        stockSymbolRegistry.removeSymbol("TEST");
    }

    @Test
    void addSymbol_duplicateSymbol_returnsFalse() {
        boolean result = stockSymbolRegistry.addSymbol("AAPL", "Apple Inc");

        assertFalse(result);
    }

    @Test
    void addSymbol_nullOrEmpty_returnsFalse() {
        assertFalse(stockSymbolRegistry.addSymbol(null, "Test"));
        assertFalse(stockSymbolRegistry.addSymbol("", "Test"));
        assertFalse(stockSymbolRegistry.addSymbol("TEST", null));
        assertFalse(stockSymbolRegistry.addSymbol("TEST", ""));
    }

    @Test
    void removeSymbol_existingSymbol_returnsTrue() {
        stockSymbolRegistry.addSymbol("TEST", "Test Company");

        boolean result = stockSymbolRegistry.removeSymbol("TEST");

        assertTrue(result);
        assertTrue(stockSymbolRegistry.fromString("TEST").isEmpty());
    }

    @Test
    void removeSymbol_nonExistingSymbol_returnsFalse() {
        boolean result = stockSymbolRegistry.removeSymbol("NONEXISTENT");

        assertFalse(result);
    }

    @Test
    void removeSymbol_nullOrEmpty_returnsFalse() {
        assertFalse(stockSymbolRegistry.removeSymbol(null));
        assertFalse(stockSymbolRegistry.removeSymbol(""));
    }
}
