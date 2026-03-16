package org.tradelite.service;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tradelite.common.StockSymbol;

class StockSymbolRegistryTest {

    private StockSymbolRegistry stockSymbolRegistry;

    @TempDir File tempDir;

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

    @Test
    void fromString_caseInsensitive_findsSymbol() {
        Optional<StockSymbol> result = stockSymbolRegistry.fromString("aapl");

        assertTrue(result.isPresent());
        assertThat(result.get().getTicker(), is("AAPL"));
    }

    @Test
    void addSymbol_convertsToUpperCase() {
        boolean result = stockSymbolRegistry.addSymbol("test", "Test Company");

        assertTrue(result);
        Optional<StockSymbol> added = stockSymbolRegistry.fromString("TEST");
        assertTrue(added.isPresent());
        assertThat(added.get().getTicker(), is("TEST"));

        // Cleanup
        stockSymbolRegistry.removeSymbol("TEST");
    }

    @Test
    void addSymbol_duplicateCaseInsensitive_returnsFalse() {
        boolean result = stockSymbolRegistry.addSymbol("aapl", "Apple");

        assertFalse(result);
    }

    @Test
    void removeSymbol_caseInsensitive_returnsTrue() {
        stockSymbolRegistry.addSymbol("TEST", "Test Company");

        boolean result = stockSymbolRegistry.removeSymbol("test");

        assertTrue(result);
        assertTrue(stockSymbolRegistry.fromString("TEST").isEmpty());
    }

    @Test
    void stockSymbolEntry_defaultConstructor_createsInstance() {
        StockSymbolRegistry.StockSymbolEntry entry = new StockSymbolRegistry.StockSymbolEntry();

        assertNotNull(entry);
        assertNull(entry.getTicker());
        assertNull(entry.getDisplayName());
    }

    @Test
    void stockSymbolEntry_parameterizedConstructor_setsValues() {
        StockSymbolRegistry.StockSymbolEntry entry =
                new StockSymbolRegistry.StockSymbolEntry("AAPL", "Apple Inc");

        assertNotNull(entry);
        assertThat(entry.getTicker(), is("AAPL"));
        assertThat(entry.getDisplayName(), is("Apple Inc"));
    }

    @Test
    void stockSymbolEntry_setters_updateValues() {
        StockSymbolRegistry.StockSymbolEntry entry = new StockSymbolRegistry.StockSymbolEntry();

        entry.setTicker("MSFT");
        entry.setDisplayName("Microsoft");

        assertThat(entry.getTicker(), is("MSFT"));
        assertThat(entry.getDisplayName(), is("Microsoft"));
    }

    @Test
    void loadStockSymbols_invalidFile_throwsIOException() throws Exception {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        when(mockMapper.readValue(
                        any(java.io.InputStream.class),
                        any(com.fasterxml.jackson.core.type.TypeReference.class)))
                .thenThrow(new IOException("Test exception"));

        assertThrows(IOException.class, () -> new StockSymbolRegistry(mockMapper));
    }

    @Test
    void getAll_returnsNewListEachTime() {
        List<StockSymbol> list1 = stockSymbolRegistry.getAll();
        List<StockSymbol> list2 = stockSymbolRegistry.getAll();

        assertNotSame(list1, list2);
        assertEquals(list1.size(), list2.size());
    }

    @Test
    void isEtf_etfSymbol_returnsTrue() {
        assertTrue(stockSymbolRegistry.isEtf("SPY"));
        assertTrue(stockSymbolRegistry.isEtf("XLK"));
        assertTrue(stockSymbolRegistry.isEtf("XLF"));
        assertTrue(stockSymbolRegistry.isEtf("XLE"));
        assertTrue(stockSymbolRegistry.isEtf("XLV"));
        assertTrue(stockSymbolRegistry.isEtf("XLY"));
        assertTrue(stockSymbolRegistry.isEtf("XLP"));
        assertTrue(stockSymbolRegistry.isEtf("XLI"));
        assertTrue(stockSymbolRegistry.isEtf("XLC"));
        assertTrue(stockSymbolRegistry.isEtf("XLRE"));
        assertTrue(stockSymbolRegistry.isEtf("XLB"));
        assertTrue(stockSymbolRegistry.isEtf("XLU"));
    }

    @Test
    void isEtf_regularStock_returnsFalse() {
        assertFalse(stockSymbolRegistry.isEtf("AAPL"));
        assertFalse(stockSymbolRegistry.isEtf("MSFT"));
        assertFalse(stockSymbolRegistry.isEtf("GOOGL"));
    }

    @Test
    void isEtf_nullOrEmpty_returnsFalse() {
        assertFalse(stockSymbolRegistry.isEtf(null));
        assertFalse(stockSymbolRegistry.isEtf(""));
    }

    @Test
    void isEtf_caseInsensitive_returnsTrue() {
        assertTrue(stockSymbolRegistry.isEtf("spy"));
        assertTrue(stockSymbolRegistry.isEtf("Spy"));
        assertTrue(stockSymbolRegistry.isEtf("xlk"));
    }
}
