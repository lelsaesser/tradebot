package org.tradelite.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
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
