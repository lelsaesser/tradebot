package org.tradelite.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Central registry of all tracked symbols: ETFs (sector + thematic + benchmark) and individual
 * stocks.
 *
 * <p>ETF definitions are hardcoded constants. Stocks are loaded from {@code
 * config/stock-symbols.json} and can be added/removed at runtime via Telegram commands.
 */
@Slf4j
@Service
public class SymbolRegistry {

    /** The benchmark ETF symbol. */
    public static final String BENCHMARK_SYMBOL = "SPY";

    /** The benchmark ETF display name. */
    public static final String BENCHMARK_NAME = "S&P 500";

    /** Broad SPDR sector ETF symbols with their display names. */
    public static final Map<String, String> BROAD_SECTOR_ETFS =
            Map.ofEntries(
                    Map.entry("XLK", "Technology"),
                    Map.entry("XLF", "Financials"),
                    Map.entry("XLE", "Energy"),
                    Map.entry("XLV", "Health Care"),
                    Map.entry("XLY", "Cons. Discretionary"),
                    Map.entry("XLP", "Cons. Staples"),
                    Map.entry("XLI", "Industrials"),
                    Map.entry("XLC", "Communication"),
                    Map.entry("XLRE", "Real Estate"),
                    Map.entry("XLB", "Materials"),
                    Map.entry("XLU", "Utilities"));

    /** Thematic / industry-specific ETF symbols with their display names. */
    public static final Map<String, String> THEMATIC_ETFS =
            Map.ofEntries(
                    Map.entry("SMH", "Semiconductors"),
                    Map.entry("SHLD", "Defence Tech"),
                    Map.entry("IGV", "Software"),
                    Map.entry("XOP", "Oil & Gas"),
                    Map.entry("XHB", "Homebuilders"),
                    Map.entry("ITA", "Aerospace & Defence"),
                    Map.entry("XBI", "Biotech"),
                    Map.entry("UFO", "Space"),
                    Map.entry("TAN", "Solar"),
                    Map.entry("URA", "Uranium & Nuclear"),
                    Map.entry("REMX", "Rare Earths"),
                    Map.entry("QTUM", "Quantum"),
                    Map.entry("DTCR", "Data Centers"),
                    Map.entry("FINX", "FinTech"),
                    Map.entry("LIT", "Batteries"),
                    Map.entry("BOTZ", "Robotics"),
                    Map.entry("STCE", "Crypto"));

    private static final String STOCK_SYMBOLS_FILE = "config/stock-symbols.json";

    private static final Set<String> SECTOR_ETF_SYMBOLS;
    private static final Set<String> ETF_SYMBOLS;

    static {
        Set<String> sectorSet = new LinkedHashSet<>(BROAD_SECTOR_ETFS.keySet());
        sectorSet.add(BENCHMARK_SYMBOL);
        SECTOR_ETF_SYMBOLS = Set.copyOf(sectorSet);

        Set<String> allEtfSet = new LinkedHashSet<>(sectorSet);
        allEtfSet.addAll(THEMATIC_ETFS.keySet());
        ETF_SYMBOLS = Set.copyOf(allEtfSet);
    }

    private final ObjectMapper objectMapper;
    private List<StockSymbolEntry> stockSymbols;

    @Autowired
    public SymbolRegistry(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        loadStockSymbols();
    }

    /** Returns all tracked symbols: ETFs (with benchmark) + non-ETF stocks, deduplicated. */
    public List<StockSymbol> getAll() {
        Set<String> seen = new LinkedHashSet<>();
        List<StockSymbol> result = new ArrayList<>();

        for (Map.Entry<String, String> entry : getAllEtfs().entrySet()) {
            if (seen.add(entry.getKey())) {
                result.add(new StockSymbol(entry.getKey(), entry.getValue()));
            }
        }

        for (StockSymbolEntry entry : stockSymbols) {
            if (!isEtf(entry.getTicker()) && seen.add(entry.getTicker())) {
                result.add(new StockSymbol(entry.getTicker(), entry.getDisplayName()));
            }
        }

        return result;
    }

    /** Returns all ETFs including benchmark as a ticker-to-display-name map. */
    public Map<String, String> getAllEtfs() {
        Map<String, String> all = new LinkedHashMap<>();
        all.put(BENCHMARK_SYMBOL, BENCHMARK_NAME);
        all.putAll(BROAD_SECTOR_ETFS);
        all.putAll(THEMATIC_ETFS);
        return all;
    }

    /** Returns the set of thematic ETF symbols for report section splitting. */
    public Set<String> getThematicSymbols() {
        return THEMATIC_ETFS.keySet();
    }

    /** Returns non-ETF stocks only. */
    public List<StockSymbol> getStocks() {
        return stockSymbols.stream()
                .filter(entry -> !isEtf(entry.getTicker()))
                .map(entry -> new StockSymbol(entry.getTicker(), entry.getDisplayName()))
                .toList();
    }

    /** Checks if the ticker is any ETF (sector, thematic, or benchmark). */
    public boolean isEtf(String ticker) {
        if (ticker == null || ticker.isEmpty()) {
            return false;
        }
        return ETF_SYMBOLS.contains(ticker.toUpperCase());
    }

    /** Checks if the ticker is a broad SPDR sector ETF or the benchmark. */
    public boolean isSectorEtf(String ticker) {
        if (ticker == null || ticker.isEmpty()) {
            return false;
        }
        return SECTOR_ETF_SYMBOLS.contains(ticker.toUpperCase());
    }

    /** Looks up a symbol by ticker across ETFs and stocks. */
    public Optional<StockSymbol> fromString(String ticker) {
        if (ticker == null || ticker.isEmpty()) {
            return Optional.empty();
        }

        String upperTicker = ticker.toUpperCase();
        Map<String, String> etfs = getAllEtfs();
        if (etfs.containsKey(upperTicker)) {
            return Optional.of(new StockSymbol(upperTicker, etfs.get(upperTicker)));
        }

        return stockSymbols.stream()
                .filter(entry -> entry.getTicker().equalsIgnoreCase(ticker))
                .findFirst()
                .map(entry -> new StockSymbol(entry.getTicker(), entry.getDisplayName()));
    }

    /** Adds a user stock symbol. Cannot add ETFs (they are hardcoded). */
    public synchronized boolean addSymbol(String ticker, String displayName) {
        if (ticker == null || ticker.isEmpty() || displayName == null || displayName.isEmpty()) {
            return false;
        }

        boolean exists =
                stockSymbols.stream().anyMatch(entry -> entry.getTicker().equalsIgnoreCase(ticker));

        if (exists) {
            log.warn("Stock symbol {} already exists", ticker);
            return false;
        }

        StockSymbolEntry newEntry = new StockSymbolEntry(ticker.toUpperCase(), displayName);
        stockSymbols.add(newEntry);

        try {
            saveStockSymbols();
            log.info("Added stock symbol: {} ({})", ticker, displayName);
            return true;
        } catch (IOException e) {
            log.error("Failed to save stock symbol: {}", ticker, e);
            stockSymbols.remove(newEntry);
            return false;
        }
    }

    /** Removes a user stock symbol. Cannot remove ETFs (they are hardcoded). */
    public synchronized boolean removeSymbol(String ticker) {
        if (ticker == null || ticker.isEmpty()) {
            return false;
        }

        boolean removed =
                stockSymbols.removeIf(entry -> entry.getTicker().equalsIgnoreCase(ticker));

        if (removed) {
            try {
                saveStockSymbols();
                log.info("Removed stock symbol: {}", ticker);
                return true;
            } catch (IOException e) {
                log.error("Failed to save after removing stock symbol: {}", ticker, e);
                return false;
            }
        }

        return false;
    }

    protected void loadStockSymbols() throws IOException {
        File file = new File(STOCK_SYMBOLS_FILE);
        try (InputStream inputStream = new FileInputStream(file)) {
            stockSymbols = objectMapper.readValue(inputStream, new TypeReference<>() {});
            log.info("Loaded {} stock symbols from {}", stockSymbols.size(), STOCK_SYMBOLS_FILE);
        } catch (IOException e) {
            log.error("Failed to load stock symbols from {}", STOCK_SYMBOLS_FILE, e);
            stockSymbols = new ArrayList<>();
            throw e;
        }
    }

    protected void saveStockSymbols() throws IOException {
        File file = new File(STOCK_SYMBOLS_FILE);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, stockSymbols);
    }

    @Setter
    @Getter
    @NoArgsConstructor
    public static class StockSymbolEntry {
        private String ticker;
        private String displayName;

        public StockSymbolEntry(String ticker, String displayName) {
            this.ticker = ticker;
            this.displayName = displayName;
        }
    }
}
