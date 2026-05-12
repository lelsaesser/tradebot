package org.tradelite.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.repository.ApiMeteringRecord;
import org.tradelite.repository.ApiMeteringRepository;

@Slf4j
@Service
public class ApiRequestMeteringService {

    static final String FINNHUB = "finnhub";
    static final String COINGECKO = "coingecko";
    static final String TWELVEDATA = "twelvedata";
    static final String YAHOO = "yahoo";

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    static final Map<String, String> LEGACY_FILES =
            Map.of(
                    FINNHUB, "config/finnhub-monthly-requests.txt",
                    COINGECKO, "config/coingecko-monthly-requests.txt",
                    TWELVEDATA, "config/twelvedata-monthly-requests.txt",
                    YAHOO, "config/yahoo-monthly-requests.txt");

    private final ApiMeteringRepository repository;
    private final Map<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    ApiRequestMeteringService(ApiMeteringRepository repository) {
        this.repository = repository;
        counters.put(FINNHUB, new AtomicInteger(0));
        counters.put(COINGECKO, new AtomicInteger(0));
        counters.put(TWELVEDATA, new AtomicInteger(0));
        counters.put(YAHOO, new AtomicInteger(0));
    }

    @PostConstruct
    void startup() {
        migrateLegacyFilesIfNeeded();
        initializeCounters();
    }

    public void incrementFinnhubRequests() {
        int newCount = counters.get(FINNHUB).incrementAndGet();
        log.info("Finnhub API request count for {}: {}", getCurrentMonth(), newCount);
    }

    public void incrementCoingeckoRequests() {
        int newCount = counters.get(COINGECKO).incrementAndGet();
        log.info("CoinGecko API request count for {}: {}", getCurrentMonth(), newCount);
    }

    public void incrementTwelveDataRequests() {
        int newCount = counters.get(TWELVEDATA).incrementAndGet();
        log.info("TwelveData API request count for {}: {}", getCurrentMonth(), newCount);
    }

    public void incrementYahooRequests() {
        int newCount = counters.get(YAHOO).incrementAndGet();
        log.info("Yahoo Finance API request count for {}: {}", getCurrentMonth(), newCount);
    }

    public int getFinnhubRequestCount() {
        return counters.get(FINNHUB).get();
    }

    public int getCoingeckoRequestCount() {
        return counters.get(COINGECKO).get();
    }

    public int getTwelveDataRequestCount() {
        return counters.get(TWELVEDATA).get();
    }

    public int getYahooRequestCount() {
        return counters.get(YAHOO).get();
    }

    /** Get the current month in YYYY-MM format */
    public String getCurrentMonth() {
        return LocalDateTime.now().format(MONTH_FORMATTER);
    }

    /** Get the previous month in YYYY-MM format (for reports that run on the 1st of each month) */
    public String getPreviousMonth() {
        return LocalDateTime.now().minusMonths(1).format(MONTH_FORMATTER);
    }

    public String getRequestCountSummary() {
        return String.format(
                "API Request Counts for %s - Finnhub: %d, CoinGecko: %d, TwelveData: %d, Yahoo: %d",
                getCurrentMonth(),
                counters.get(FINNHUB).get(),
                counters.get(COINGECKO).get(),
                counters.get(TWELVEDATA).get(),
                counters.get(YAHOO).get());
    }

    public void resetCounters() {
        log.info("Resetting counters for month: {}", getCurrentMonth());
        counters.values().forEach(counter -> counter.set(0));
        flushCounters();
        log.info("Counters reset successfully");
    }

    public void flushCounters() {
        LocalDateTime now = LocalDateTime.now();
        String month = getCurrentMonth();
        List<ApiMeteringRecord> records =
                counters.entrySet().stream()
                        .map(
                                entry ->
                                        new ApiMeteringRecord(
                                                entry.getKey(), month, entry.getValue().get(), now))
                        .toList();

        try {
            repository.saveAll(records);
            log.debug("Flushed API metering counters to database");
        } catch (Exception e) {
            log.warn("Failed to flush API metering counters to database", e);
        }
    }

    @PreDestroy
    void shutdown() {
        log.info("Shutting down API metering service, flushing counters...");
        flushCounters();
    }

    void migrateLegacyFilesIfNeeded() {
        String currentMonth = getCurrentMonth();
        List<ApiMeteringRecord> existing = repository.findByMonth(currentMonth);

        for (Map.Entry<String, String> entry : LEGACY_FILES.entrySet()) {
            String provider = entry.getKey();
            String filePath = entry.getValue();

            if (existing.stream().anyMatch(r -> r.provider().equals(provider))) {
                log.debug("Provider {} already has data, skipping migration", provider);
                continue;
            }

            File file = new File(filePath);
            if (!file.exists()) {
                continue;
            }

            try {
                ApiMeteringRecord meteringRecord = parseLegacyFile(file, provider, currentMonth);
                if (meteringRecord != null) {
                    repository.saveAll(List.of(meteringRecord));
                    log.info(
                            "Migrated legacy metering file for {}: count={}",
                            provider,
                            meteringRecord.count());
                }
            } catch (Exception e) {
                log.warn("Failed to migrate legacy metering file: {}", filePath, e);
            }
        }
    }

    ApiMeteringRecord parseLegacyFile(File file, String provider, String currentMonth)
            throws IOException {
        List<String> lines = Files.readAllLines(file.toPath());

        String month = null;
        int count = 0;
        LocalDateTime lastUpdated = LocalDateTime.now();

        for (String line : lines) {
            if (line.startsWith("Month:")) {
                month = line.substring("Month:".length()).trim();
            } else if (line.startsWith("Count:")) {
                count = Integer.parseInt(line.substring("Count:".length()).trim());
            } else if (line.startsWith("Last Updated:")) {
                try {
                    lastUpdated =
                            LocalDateTime.parse(line.substring("Last Updated:".length()).trim());
                } catch (Exception _) {
                    log.debug("Could not parse Last Updated timestamp, using current time");
                }
            }
        }

        if (month == null) {
            log.warn("No month found in legacy file for provider {}", provider);
            return null;
        }

        if (!month.equals(currentMonth)) {
            log.debug(
                    "Skipping stale legacy file for {}: file month={}, current={}",
                    provider,
                    month,
                    currentMonth);
            return null;
        }

        return new ApiMeteringRecord(provider, month, count, lastUpdated);
    }

    private void initializeCounters() {
        String month = getCurrentMonth();
        List<ApiMeteringRecord> records = repository.findByMonth(month);

        for (ApiMeteringRecord meteringRecord : records) {
            AtomicInteger counter = counters.get(meteringRecord.provider());
            if (counter == null) {
                log.warn("Unknown provider in database: {}", meteringRecord.provider());
                continue;
            }

            counter.set(meteringRecord.count());
            log.info(
                    "Initialized {} counter for {}: {}",
                    meteringRecord.provider(),
                    month,
                    meteringRecord.count());
        }
    }
}
