package org.tradelite.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.common.StockSymbol;

@Slf4j
@Service
public class StockSymbolRegistry {

    private static final String STOCK_SYMBOLS_FILE = "config/stock-symbols.json";

    private final ObjectMapper objectMapper;
    private List<StockSymbolEntry> stockSymbols;

    @Autowired
    public StockSymbolRegistry(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        loadStockSymbols();
    }

    public Optional<StockSymbol> fromString(String ticker) {
        if (ticker == null || ticker.isEmpty()) {
            return Optional.empty();
        }

        return stockSymbols.stream()
                .filter(entry -> entry.getTicker().equalsIgnoreCase(ticker))
                .findFirst()
                .map(entry -> new StockSymbol(entry.getTicker(), entry.getDisplayName()));
    }

    public List<StockSymbol> getAll() {
        return stockSymbols.stream()
                .map(entry -> new StockSymbol(entry.getTicker(), entry.getDisplayName()))
                .toList();
    }

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
