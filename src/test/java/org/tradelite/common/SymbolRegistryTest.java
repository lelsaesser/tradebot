package org.tradelite.common;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SymbolRegistryTest {

    private SymbolRegistry symbolRegistry;

    @TempDir File tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        symbolRegistry = new SymbolRegistry(objectMapper);
    }

    @Test
    void fromString_existingSymbol_returnsStockSymbol() {
        Optional<StockSymbol> result = symbolRegistry.fromString("AAPL");

        assertTrue(result.isPresent());
        assertThat(result.get().getTicker(), is("AAPL"));
        assertThat(result.get().getCompanyName(), is("Apple"));
    }

    @Test
    void fromString_nonExistingSymbol_returnsEmpty() {
        Optional<StockSymbol> result = symbolRegistry.fromString("INVALID");

        assertTrue(result.isEmpty());
    }

    @Test
    void fromString_nullOrEmpty_returnsEmpty() {
        assertTrue(symbolRegistry.fromString(null).isEmpty());
        assertTrue(symbolRegistry.fromString("").isEmpty());
    }

    @Test
    void getAll_returnsAllSymbolsIncludingEtfs() {
        List<StockSymbol> result = symbolRegistry.getAll();

        assertFalse(result.isEmpty());
        // Should include ETFs
        assertTrue(result.stream().anyMatch(s -> s.getTicker().equals("SPY")));
        assertTrue(result.stream().anyMatch(s -> s.getTicker().equals("XLK")));
        // Should include stocks
        assertTrue(result.stream().anyMatch(s -> s.getTicker().equals("AAPL")));
    }

    @Test
    void getStocks_returnsOnlyNonEtfStocks() {
        List<StockSymbol> stocks = symbolRegistry.getStocks();

        assertFalse(stocks.isEmpty());
        assertTrue(stocks.stream().anyMatch(s -> s.getTicker().equals("AAPL")));
        // Should NOT include ETFs
        assertFalse(stocks.stream().anyMatch(s -> s.getTicker().equals("SPY")));
        assertFalse(stocks.stream().anyMatch(s -> s.getTicker().equals("XLK")));
        assertFalse(stocks.stream().anyMatch(s -> s.getTicker().equals("SMH")));
    }

    @Test
    void getAllEtfs_returnsAllEtfsWithBenchmark() {
        Map<String, String> etfs = symbolRegistry.getAllEtfs();

        assertFalse(etfs.isEmpty());
        assertTrue(etfs.containsKey("SPY"));
        assertTrue(etfs.containsKey("XLK"));
        assertTrue(etfs.containsKey("SMH"));
    }

    @Test
    void addSymbol_newSymbol_returnsTrue() {
        boolean result = symbolRegistry.addSymbol("TEST", "Test Company");

        assertTrue(result);
        Optional<StockSymbol> added = symbolRegistry.fromString("TEST");
        assertTrue(added.isPresent());
        assertThat(added.get().getTicker(), is("TEST"));
        assertThat(added.get().getCompanyName(), is("Test Company"));

        // Cleanup
        symbolRegistry.removeSymbol("TEST");
    }

    @Test
    void addSymbol_duplicateSymbol_returnsFalse() {
        boolean result = symbolRegistry.addSymbol("AAPL", "Apple Inc");

        assertFalse(result);
    }

    @Test
    void addSymbol_nullOrEmpty_returnsFalse() {
        assertFalse(symbolRegistry.addSymbol(null, "Test"));
        assertFalse(symbolRegistry.addSymbol("", "Test"));
        assertFalse(symbolRegistry.addSymbol("TEST", null));
        assertFalse(symbolRegistry.addSymbol("TEST", ""));
    }

    @Test
    void removeSymbol_existingSymbol_returnsTrue() {
        symbolRegistry.addSymbol("TEST", "Test Company");

        boolean result = symbolRegistry.removeSymbol("TEST");

        assertTrue(result);
        assertTrue(symbolRegistry.fromString("TEST").isEmpty());
    }

    @Test
    void removeSymbol_nonExistingSymbol_returnsFalse() {
        boolean result = symbolRegistry.removeSymbol("NONEXISTENT");

        assertFalse(result);
    }

    @Test
    void removeSymbol_nullOrEmpty_returnsFalse() {
        assertFalse(symbolRegistry.removeSymbol(null));
        assertFalse(symbolRegistry.removeSymbol(""));
    }

    @Test
    void fromString_caseInsensitive_findsSymbol() {
        Optional<StockSymbol> result = symbolRegistry.fromString("aapl");

        assertTrue(result.isPresent());
        assertThat(result.get().getTicker(), is("AAPL"));
    }

    @Test
    void addSymbol_convertsToUpperCase() {
        boolean result = symbolRegistry.addSymbol("test", "Test Company");

        assertTrue(result);
        Optional<StockSymbol> added = symbolRegistry.fromString("TEST");
        assertTrue(added.isPresent());
        assertThat(added.get().getTicker(), is("TEST"));

        // Cleanup
        symbolRegistry.removeSymbol("TEST");
    }

    @Test
    void addSymbol_duplicateCaseInsensitive_returnsFalse() {
        boolean result = symbolRegistry.addSymbol("aapl", "Apple");

        assertFalse(result);
    }

    @Test
    void removeSymbol_caseInsensitive_returnsTrue() {
        symbolRegistry.addSymbol("TEST", "Test Company");

        boolean result = symbolRegistry.removeSymbol("test");

        assertTrue(result);
        assertTrue(symbolRegistry.fromString("TEST").isEmpty());
    }

    @Test
    void stockSymbolEntry_defaultConstructor_createsInstance() {
        SymbolRegistry.StockSymbolEntry entry = new SymbolRegistry.StockSymbolEntry();

        assertNotNull(entry);
        assertNull(entry.getTicker());
        assertNull(entry.getDisplayName());
    }

    @Test
    void stockSymbolEntry_parameterizedConstructor_setsValues() {
        SymbolRegistry.StockSymbolEntry entry =
                new SymbolRegistry.StockSymbolEntry("AAPL", "Apple Inc");

        assertNotNull(entry);
        assertThat(entry.getTicker(), is("AAPL"));
        assertThat(entry.getDisplayName(), is("Apple Inc"));
    }

    @Test
    void stockSymbolEntry_setters_updateValues() {
        SymbolRegistry.StockSymbolEntry entry = new SymbolRegistry.StockSymbolEntry();

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

        assertThrows(IOException.class, () -> new SymbolRegistry(mockMapper));
    }

    @Test
    void getAll_returnsNewListEachTime() {
        List<StockSymbol> list1 = symbolRegistry.getAll();
        List<StockSymbol> list2 = symbolRegistry.getAll();

        assertNotSame(list1, list2);
        assertEquals(list1.size(), list2.size());
    }

    @Test
    void isSectorEtf_sectorEtfSymbol_returnsTrue() {
        assertTrue(symbolRegistry.isSectorEtf("SPY"));
        assertTrue(symbolRegistry.isSectorEtf("XLK"));
        assertTrue(symbolRegistry.isSectorEtf("XLF"));
        assertTrue(symbolRegistry.isSectorEtf("XLE"));
        assertTrue(symbolRegistry.isSectorEtf("XLV"));
        assertTrue(symbolRegistry.isSectorEtf("XLY"));
        assertTrue(symbolRegistry.isSectorEtf("XLP"));
        assertTrue(symbolRegistry.isSectorEtf("XLI"));
        assertTrue(symbolRegistry.isSectorEtf("XLC"));
        assertTrue(symbolRegistry.isSectorEtf("XLRE"));
        assertTrue(symbolRegistry.isSectorEtf("XLB"));
        assertTrue(symbolRegistry.isSectorEtf("XLU"));
    }

    @Test
    void isSectorEtf_thematicEtf_returnsFalse() {
        assertFalse(symbolRegistry.isSectorEtf("SMH"));
        assertFalse(symbolRegistry.isSectorEtf("URA"));
        assertFalse(symbolRegistry.isSectorEtf("IGV"));
        assertFalse(symbolRegistry.isSectorEtf("SHLD"));
    }

    @Test
    void isSectorEtf_regularStock_returnsFalse() {
        assertFalse(symbolRegistry.isSectorEtf("AAPL"));
        assertFalse(symbolRegistry.isSectorEtf("MSFT"));
        assertFalse(symbolRegistry.isSectorEtf("GOOGL"));
    }

    @Test
    void isSectorEtf_nullOrEmpty_returnsFalse() {
        assertFalse(symbolRegistry.isSectorEtf(null));
        assertFalse(symbolRegistry.isSectorEtf(""));
    }

    @Test
    void isSectorEtf_caseInsensitive_returnsTrue() {
        assertTrue(symbolRegistry.isSectorEtf("spy"));
        assertTrue(symbolRegistry.isSectorEtf("Spy"));
        assertTrue(symbolRegistry.isSectorEtf("xlk"));
    }

    @Test
    void isEtf_sectorEtfSymbol_returnsTrue() {
        assertTrue(symbolRegistry.isEtf("SPY"));
        assertTrue(symbolRegistry.isEtf("XLK"));
        assertTrue(symbolRegistry.isEtf("XLF"));
        assertTrue(symbolRegistry.isEtf("XLE"));
        assertTrue(symbolRegistry.isEtf("XLU"));
    }

    @Test
    void isEtf_thematicEtfSymbol_returnsTrue() {
        assertTrue(symbolRegistry.isEtf("SMH"));
        assertTrue(symbolRegistry.isEtf("SHLD"));
        assertTrue(symbolRegistry.isEtf("IGV"));
        assertTrue(symbolRegistry.isEtf("XOP"));
        assertTrue(symbolRegistry.isEtf("XHB"));
        assertTrue(symbolRegistry.isEtf("ITA"));
        assertTrue(symbolRegistry.isEtf("XBI"));
        assertTrue(symbolRegistry.isEtf("UFO"));
        assertTrue(symbolRegistry.isEtf("TAN"));
        assertTrue(symbolRegistry.isEtf("URA"));
    }

    @Test
    void isEtf_regularStock_returnsFalse() {
        assertFalse(symbolRegistry.isEtf("AAPL"));
        assertFalse(symbolRegistry.isEtf("MSFT"));
        assertFalse(symbolRegistry.isEtf("GOOGL"));
    }

    @Test
    void isEtf_nullOrEmpty_returnsFalse() {
        assertFalse(symbolRegistry.isEtf(null));
        assertFalse(symbolRegistry.isEtf(""));
    }

    @Test
    void isEtf_caseInsensitive_returnsTrue() {
        assertTrue(symbolRegistry.isEtf("smh"));
        assertTrue(symbolRegistry.isEtf("Smh"));
        assertTrue(symbolRegistry.isEtf("ura"));
    }

    @Test
    void fromString_etfSymbol_returnsStockSymbol() {
        Optional<StockSymbol> result = symbolRegistry.fromString("SPY");

        assertTrue(result.isPresent());
        assertThat(result.get().getTicker(), is("SPY"));
    }

    @Test
    void fromString_thematicEtf_returnsStockSymbol() {
        Optional<StockSymbol> result = symbolRegistry.fromString("SMH");

        assertTrue(result.isPresent());
        assertThat(result.get().getTicker(), is("SMH"));
    }
}
