package org.tradelite.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.core.IgnoreReason;
import org.tradelite.core.IgnoredSymbol;

@Slf4j
@Component
public class TargetPriceProvider {

    public static final String FILE_PATH_STOCKS = "config/target-prices-stocks.json";
    public static final String FILE_PATH_COINS = "config/target-prices-coins.json";
    public static final long IGNORE_DURATION_TTL_SECONDS = 3600L * 12; // 12 hours

    protected final Map<String, IgnoredSymbol> ignoredSymbols = new ConcurrentHashMap<>();
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

    public void addIgnoredSymbol(TickerSymbol symbol, IgnoreReason reason, int alertThreshold) {
        IgnoredSymbol existingSymbol =
                ignoredSymbols.computeIfAbsent(symbol.getName(), _ -> new IgnoredSymbol(symbol));
        existingSymbol.getIgnoreTimes().put(reason, Instant.now());
        existingSymbol.getAlertThresholds().put(reason, alertThreshold);
    }

    public void addIgnoredSymbol(TickerSymbol symbol, IgnoreReason reason) {
        addIgnoredSymbol(symbol, reason, 0);
    }

    public boolean isSymbolIgnored(TickerSymbol symbol, IgnoreReason reason, int alertThreshold) {
        IgnoredSymbol ignoredSymbol = ignoredSymbols.get(symbol.getName());

        if (ignoredSymbol == null) {
            return false;
        }

        if (alertThreshold > 0) {
            Integer lastAlertedThreshold = ignoredSymbol.getAlertThresholds().get(reason);
            return lastAlertedThreshold != null && alertThreshold <= lastAlertedThreshold;
        }

        Instant ignoreTime = ignoredSymbol.getIgnoreTimes().get(reason);
        if (ignoreTime == null) {
            return false;
        }

        long elapsedSeconds = Duration.between(ignoreTime, Instant.now()).getSeconds();
        if (elapsedSeconds < IGNORE_DURATION_TTL_SECONDS) {
            log.info(
                    "{} is ignored for reason {}. Time remaining: {} seconds",
                    symbol.getName(),
                    reason,
                    IGNORE_DURATION_TTL_SECONDS - elapsedSeconds);
            return true;
        }
        return false;
    }

    public boolean isSymbolIgnored(TickerSymbol symbol, IgnoreReason reason) {
        return isSymbolIgnored(symbol, reason, 0);
    }

    public void cleanupIgnoreSymbols(long ttlSeconds) {
        Instant now = Instant.now();

        for (IgnoredSymbol ignoredSymbol : ignoredSymbols.values()) {
            Map<IgnoreReason, Instant> reasons = ignoredSymbol.getIgnoreTimes();
            List<IgnoreReason> toRemove = new ArrayList<>();
            for (Map.Entry<IgnoreReason, Instant> entry : reasons.entrySet()) {
                long duration = now.getEpochSecond() - entry.getValue().getEpochSecond();
                if (duration > ttlSeconds) {
                    toRemove.add(entry.getKey());
                }
            }
            toRemove.forEach(reasons::remove);
            toRemove.forEach(ignoredSymbol.getAlertThresholds()::remove);
        }

        ignoredSymbols.entrySet().removeIf(entry -> entry.getValue().getIgnoreTimes().isEmpty());
    }

    public synchronized void updateTargetPrice(
            TickerSymbol symbol, Double newBuyTarget, Double newSellTarget, String filePath) {
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

    public synchronized boolean addTargetPrice(TargetPrice targetPrice, String filePath) {
        List<TargetPrice> entries;
        File file = new File(filePath);
        try {
            entries = objectMapper.readValue(file, new TypeReference<>() {});

            boolean alreadyExists =
                    entries.stream()
                            .anyMatch(
                                    tp -> tp.getSymbol().equalsIgnoreCase(targetPrice.getSymbol()));

            if (alreadyExists) {
                log.warn("Symbol {} already exists in target prices", targetPrice.getSymbol());
                return false;
            }

            entries.add(targetPrice);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, entries);

            return true;

        } catch (IOException e) {
            log.error("Failed to add target price to JSON file", e);
            return false;
        }
    }

    public synchronized boolean removeSymbolFromTargetPrices(String ticker, String filePath) {
        File file = new File(filePath);
        try {
            List<TargetPrice> entries = objectMapper.readValue(file, new TypeReference<>() {});

            boolean removed = entries.removeIf(tp -> tp.getSymbol().equalsIgnoreCase(ticker));

            if (removed) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, entries);
            }

            return removed;

        } catch (IOException e) {
            log.error("Failed to remove symbol from target prices in JSON file", e);
            return false;
        }
    }
}
