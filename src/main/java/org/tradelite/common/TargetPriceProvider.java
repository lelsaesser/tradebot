package org.tradelite.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.AddCommand;
import org.tradelite.client.telegram.RemoveCommand;
import org.tradelite.core.IgnoreReason;
import org.tradelite.core.IgnoredSymbol;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TargetPriceProvider {

    public static final String FILE_PATH_STOCKS = "config/target-prices-stocks.json";
    public static final String FILE_PATH_COINS = "config/target-prices-coins.json";
    public static final long IGNORE_DURATION_TTL_SECONDS = 3600L * 4; // 4 hours

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

    public void addIgnoredSymbol(TickerSymbol symbol, IgnoreReason reason) {
        IgnoredSymbol existingSymbol = ignoredSymbols.get(symbol.getName());

        if (existingSymbol == null) {
            Map<IgnoreReason, Instant> reasonMap = new ConcurrentHashMap<>();
            reasonMap.put(reason, Instant.now());
            ignoredSymbols.put(symbol.getName(), new IgnoredSymbol(symbol, reasonMap));
        } else {
            Map<IgnoreReason, Instant> reasons = existingSymbol.getIgnoreReasons();
            reasons.putIfAbsent(reason, Instant.now());
        }
    }

    public boolean isSymbolIgnored(TickerSymbol symbol, IgnoreReason reason) {
        IgnoredSymbol ignoredSymbol = ignoredSymbols.get(symbol.getName());

        if (ignoredSymbol == null) {
            return false;
        }

        Map<IgnoreReason, Instant> reasons = ignoredSymbol.getIgnoreReasons();
        Instant ignoreTime = reasons.get(reason);

        if (ignoreTime == null) {
            return false;
        }
        long elapsedSeconds = Duration.between(ignoreTime, Instant.now()).getSeconds();
        long remainingSeconds = IGNORE_DURATION_TTL_SECONDS - elapsedSeconds;

        log.info("{} is ignored for reason {}. Time remaining: {} seconds", symbol.getName(), reason, remainingSeconds);
        return true;
    }

    public void cleanupIgnoreSymbols(long ttlSeconds) {
        Instant now = Instant.now();

        for (IgnoredSymbol ignoredSymbol : ignoredSymbols.values()) {
            Map<IgnoreReason, Instant> reasons = ignoredSymbol.getIgnoreReasons();
            List<IgnoreReason> toRemove = new ArrayList<>();
            for (Map.Entry<IgnoreReason, Instant> entry : reasons.entrySet()) {
                long duration = now.getEpochSecond() - entry.getValue().getEpochSecond();
                if (duration > ttlSeconds) {
                    toRemove.add(entry.getKey());
                }
            }
            toRemove.forEach(reasons::remove);
        }

        ignoredSymbols.entrySet().removeIf(entry -> entry.getValue().getIgnoreReasons().isEmpty());
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

    public synchronized boolean addSymbolToTargetPriceConfig(AddCommand addCommand, String filePath) {
        List<TargetPrice> entries;
        File file = new File(filePath);
        try {
            entries = objectMapper.readValue(file, new TypeReference<>() {});

            boolean alreadyExists = entries.stream()
                    .anyMatch(tp -> tp.getSymbol().equalsIgnoreCase(addCommand.getSymbol().getName()));

            if (alreadyExists) {
                log.warn("Symbol {} already exists in target prices", addCommand.getSymbol().getName());
                return false;
            }

            TargetPrice newEntry = new TargetPrice(addCommand.getSymbol().getName(), addCommand.getBuyTargetPrice(), addCommand.getSellTargetPrice());
            entries.add(newEntry);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, entries);

            return true;

        } catch (IOException e) {
            log.error("Failed to update target price in JSON file", e);
            return false;
        }
    }

    public synchronized void removeSymbolFromTargetPriceConfig(RemoveCommand command, String filePath) {
        File file = new File(filePath);
        try {
            List<TargetPrice> entries = objectMapper.readValue(file, new TypeReference<>() {});

            entries.removeIf(tp -> tp.getSymbol().equalsIgnoreCase(command.getSymbol().getName()));

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, entries);

        } catch (IOException e) {
            throw new IllegalStateException("Failed to remove symbol from target prices in JSON file", e);
        }
    }

}
