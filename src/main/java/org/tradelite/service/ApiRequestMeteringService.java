package org.tradelite.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ApiRequestMeteringService {

    private static final String DEFAULT_COUNTER_DIR = "config";
    private static final String FINNHUB_COUNTER_FILE = "finnhub-monthly-requests.txt";
    private static final String COINGECKO_COUNTER_FILE = "coingecko-monthly-requests.txt";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AtomicInteger finnhubCounter = new AtomicInteger(0);
    private final AtomicInteger coingeckoCounter = new AtomicInteger(0);
    private volatile String currentMonth;
    private final String counterDir;

    public ApiRequestMeteringService() {
        this(DEFAULT_COUNTER_DIR);
    }

    // Package-private constructor for testing
    ApiRequestMeteringService(String counterDir) {
        this.counterDir = counterDir;
        this.currentMonth = getCurrentMonth();
        initializeCounters();
    }

    public void incrementFinnhubRequests() {
        int newCount = finnhubCounter.incrementAndGet();
        log.info("Finnhub API request count for {}: {}", currentMonth, newCount);
        persistCounter(FINNHUB_COUNTER_FILE, newCount);
    }

    public void incrementCoingeckoRequests() {
        int newCount = coingeckoCounter.incrementAndGet();
        log.info("CoinGecko API request count for {}: {}", currentMonth, newCount);
        persistCounter(COINGECKO_COUNTER_FILE, newCount);
    }

    public int getFinnhubRequestCount() {
        return finnhubCounter.get();
    }

    public int getCoingeckoRequestCount() {
        return coingeckoCounter.get();
    }

    /**
     * Get the current month in YYYY-MM format
     */
    public String getCurrentMonth() {
        return LocalDateTime.now().format(MONTH_FORMATTER);
    }

    public String getRequestCountSummary() {
        return String.format("API Request Counts for %s - Finnhub: %d, CoinGecko: %d", 
                currentMonth, finnhubCounter.get(), coingeckoCounter.get());
    }

    public void resetCounters() {
        log.info("Resetting counters for month: {}", currentMonth);
        finnhubCounter.set(0);
        coingeckoCounter.set(0);
        persistCounter(FINNHUB_COUNTER_FILE, 0);
        persistCounter(COINGECKO_COUNTER_FILE, 0);
        log.info("Counters reset successfully");
    }

    private void initializeCounters() {
        try {
            Path configDir = Paths.get(counterDir);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                log.info("Created config directory: {}", configDir.toAbsolutePath());
            }

            int finnhubCount = readCounterFromFile(FINNHUB_COUNTER_FILE);
            finnhubCounter.set(finnhubCount);
            log.info("Initialized Finnhub counter for {}: {}", currentMonth, finnhubCount);

            int coingeckoCount = readCounterFromFile(COINGECKO_COUNTER_FILE);
            coingeckoCounter.set(coingeckoCount);
            log.info("Initialized CoinGecko counter for {}: {}", currentMonth, coingeckoCount);

        } catch (IOException e) {
            log.error("Failed to initialize counters", e);
        }
    }

    private int readCounterFromFile(String filename) throws IOException {
        Path filePath = Paths.get(counterDir, filename);
        
        if (!Files.exists(filePath)) {
            // Create file with initial content if it doesn't exist
            String initialContent = String.format("Month: %s%nCount: 0%nLast Updated: %s%n",
                    currentMonth, LocalDateTime.now());
            Files.writeString(filePath, initialContent);
            return 0;
        }

        String content = Files.readString(filePath);
        String[] lines = content.split("\n");
        
        // Check if the file is for the current month
        if (lines.length >= 2) {
            String fileMonth = lines[0].replace("Month: ", "").trim();
            if (!fileMonth.equals(currentMonth)) {
                // File is from a previous month, but don't reset automatically
                // Let the cron job handle month transitions
                log.info("Counter file {} is from previous month ({}), current count will be preserved until cron job resets", filename, fileMonth);
            }
            
            // Extract count from second line
            String countLine = lines[1].replace("Count: ", "").trim();
            try {
                return Integer.parseInt(countLine);
            } catch (NumberFormatException _) {
                log.warn("Invalid count format in {}: {}", filename, countLine);
                return 0;
            }
        }
        
        return 0;
    }

    private void persistCounter(String filename, int count) {
        try {
            Path filePath = Paths.get(counterDir, filename);
            String content = String.format("Month: %s%nCount: %d%nLast Updated: %s%n",
                    currentMonth, count, LocalDateTime.now());
            Files.writeString(filePath, content);
        } catch (IOException e) {
            log.error("Failed to persist counter to file {}: {}", filename, e.getMessage());
        }
    }
}
