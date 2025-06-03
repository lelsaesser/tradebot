package org.tradelite.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class TargetPriceProvider {

    private static final String FILE_PATH_STOCKS = "config/target-prices-stocks.json";
    private static final String FILE_PATH_COINS = "config/target-prices-coins.json";

    private final Map<String, Date> ignoredSymbols = new HashMap<>();
    private final ObjectMapper objectMapper;

    @Autowired
    public TargetPriceProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TargetPrice> getStockTargetPrices() {
        return loadTargetPrices(FILE_PATH_STOCKS);
    }

    public List<TargetPrice> getCoinTargetPrices() {
        return loadTargetPrices(FILE_PATH_COINS);
    }

    protected List<TargetPrice> loadTargetPrices(String filePath) {
        File file = new File(filePath);
        try (InputStream inputStream = new FileInputStream(file)) {
            return objectMapper.readValue(inputStream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load target prices from JSON file", e);
        }
    }

    public void addIgnoredSymbol(TickerSymbol symbol) {
        ignoredSymbols.put(symbol.getName(), new Date());
    }

    public boolean isSymbolIgnored(TickerSymbol symbol) {
        Date ignoredDate = ignoredSymbols.get(symbol.getName());
        return ignoredDate != null;
    }

    public void cleanupIgnoreSymbols() {
        long maxIgnoredDuration = 3600L; // 1 hour in seconds
        Instant now = Instant.now();
        ignoredSymbols.entrySet().removeIf(entry -> {
            Instant ignoredTime = entry.getValue().toInstant();
            return now.minusSeconds(maxIgnoredDuration).isAfter(ignoredTime);
        });
    }

    public synchronized void updateTargetPrice(TickerSymbol symbol, Double newBuyTarget, Double newSellTarget, String filePath) {
        File file = new File(filePath);

        try {
            List<TargetPrice> prices = objectMapper.readValue(file, new TypeReference<>() {});

            for (TargetPrice tp : prices) {
                if (tp.getSymbol().equalsIgnoreCase(symbol.getName())) {
                    if (newBuyTarget != null) {
                        tp.setBuyTarget(newBuyTarget);
                    }
                    if (newSellTarget != null) {
                        tp.setSellTarget(newSellTarget);
                    }
                    break;
                }
            }

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, prices);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to update target prices in JSON file", e);
        }
    }

}
