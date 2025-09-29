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
    private String currentMonth;
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

    /**
     * Increment Finnhub API request counter
     */
    public void incrementFinnhubRequests() {
        checkAndResetIfNewMonth();
        int newCount = finnhubCounter.incrementAndGet();
        log.info("Finnhub API request count for {}: {}", currentMonth, newCount);
        persistCounter(FINNHUB_COUNTER_FILE, newCount);
    }

    /**
     * Increment CoinGecko API request counter
     */
    public void incrementCoingeckoRequests() {
        checkAndResetIfNewMonth();
        int newCount = coingeckoCounter.incrementAndGet();
        log.info("CoinGecko API request count for {}: {}", currentMonth, newCount);
        persistCounter(COINGECKO_COUNTER_FILE, newCount);
    }

    /**
     * Get current Finnhub request count
     */
    public int getFinnhubRequestCount() {
        checkAndResetIfNewMonth();
        return finnhubCounter.get();
    }

    /**
     * Get current CoinGecko request count
     */
    public int getCoingeckoRequestCount() {
        checkAndResetIfNewMonth();
        return coingeckoCounter.get();
    }

    /**
     * Get current month in YYYY-MM format
     */
    protected String getCurrentMonth() {
        return LocalDateTime.now().format(MONTH_FORMATTER);
    }

    /**
     * Check if we've entered a new month and reset counters if needed
     */
    private synchronized void checkAndResetIfNewMonth() {
        String newMonth = getCurrentMonth();
        if (!newMonth.equals(currentMonth)) {
            log.info("New month detected. Resetting counters from {} to {}", currentMonth, newMonth);
            
            // Log final counts for the previous month
            log.info("Final counts for {}: Finnhub={}, CoinGecko={}", 
                    currentMonth, finnhubCounter.get(), coingeckoCounter.get());
            
            // Reset counters
            finnhubCounter.set(0);
            coingeckoCounter.set(0);
            currentMonth = newMonth;
            
            // Persist reset counters
            persistCounter(FINNHUB_COUNTER_FILE, 0);
            persistCounter(COINGECKO_COUNTER_FILE, 0);
            
            log.info("Counters reset for new month: {}", currentMonth);
        }
    }

    /**
     * Initialize counters by reading from persistent files
     */
    private void initializeCounters() {
        try {
            // Ensure config directory exists
            Path configDir = Paths.get(counterDir);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
                log.info("Created config directory: {}", configDir.toAbsolutePath());
            }

            // Initialize Finnhub counter
            int finnhubCount = readCounterFromFile(FINNHUB_COUNTER_FILE);
            finnhubCounter.set(finnhubCount);
            log.info("Initialized Finnhub counter for {}: {}", currentMonth, finnhubCount);

            // Initialize CoinGecko counter
            int coingeckoCount = readCounterFromFile(COINGECKO_COUNTER_FILE);
            coingeckoCounter.set(coingeckoCount);
            log.info("Initialized CoinGecko counter for {}: {}", currentMonth, coingeckoCount);

        } catch (IOException e) {
            log.error("Failed to initialize counters", e);
            // Continue with zero counters if file reading fails
        }
    }

    /**
     * Read counter value from file
     */
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
                // File is from a previous month, reset to 0
                log.info("Counter file {} is from previous month ({}), resetting to 0", filename, fileMonth);
                return 0;
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

    /**
     * Persist counter value to file
     */
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

    /**
     * Get summary of current request counts
     */
    public String getRequestCountSummary() {
        checkAndResetIfNewMonth();
        return String.format("API Request Counts for %s - Finnhub: %d, CoinGecko: %d", 
                currentMonth, finnhubCounter.get(), coingeckoCounter.get());
    }
}
