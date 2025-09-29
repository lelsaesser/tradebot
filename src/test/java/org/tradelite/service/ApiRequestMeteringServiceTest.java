package org.tradelite.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class ApiRequestMeteringServiceTest {

    @TempDir
    Path tempDir;

    private ApiRequestMeteringService meteringService;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @BeforeEach
    void setUp() {
        // Use temp directory for testing
        Path configDir = tempDir.resolve("config");
        meteringService = new ApiRequestMeteringService(configDir.toString());
    }

    @Test
    void testIncrementFinnhubRequests() {
        // Initially should be 0
        assertEquals(0, meteringService.getFinnhubRequestCount());

        // Increment and verify
        meteringService.incrementFinnhubRequests();
        assertEquals(1, meteringService.getFinnhubRequestCount());

        meteringService.incrementFinnhubRequests();
        assertEquals(2, meteringService.getFinnhubRequestCount());
    }

    @Test
    void testIncrementCoingeckoRequests() {
        // Initially should be 0
        assertEquals(0, meteringService.getCoingeckoRequestCount());

        // Increment and verify
        meteringService.incrementCoingeckoRequests();
        assertEquals(1, meteringService.getCoingeckoRequestCount());

        meteringService.incrementCoingeckoRequests();
        assertEquals(2, meteringService.getCoingeckoRequestCount());
    }

    @Test
    void testBothCountersIndependent() {
        // Increment Finnhub
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();

        // Increment CoinGecko
        meteringService.incrementCoingeckoRequests();

        // Verify they are independent
        assertEquals(2, meteringService.getFinnhubRequestCount());
        assertEquals(1, meteringService.getCoingeckoRequestCount());
    }

    @Test
    void testFilePersistence() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        // Increment counters
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementCoingeckoRequests();

        // Check that files are created
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");

        assertTrue(Files.exists(finnhubFile));
        assertTrue(Files.exists(coingeckoFile));

        // Check file contents
        String finnhubContent = Files.readString(finnhubFile);
        String coingeckoContent = Files.readString(coingeckoFile);

        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);
        assertTrue(finnhubContent.contains("Month: " + currentMonth));
        assertTrue(finnhubContent.contains("Count: 1"));
        assertTrue(coingeckoContent.contains("Month: " + currentMonth));
        assertTrue(coingeckoContent.contains("Count: 2"));
    }

    @Test
    void testGetRequestCountSummary() {
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();

        String summary = meteringService.getRequestCountSummary();
        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);

        assertTrue(summary.contains(currentMonth));
        assertTrue(summary.contains("Finnhub: 2"));
        assertTrue(summary.contains("CoinGecko: 1"));
    }

    @Test
    void testInitializationFromExistingFiles() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);

        // Create existing files with some counts
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");

        String finnhubContent = String.format("Month: %s%nCount: 5%nLast Updated: %s%n",
                currentMonth, LocalDateTime.now());
        String coingeckoContent = String.format("Month: %s%nCount: 3%nLast Updated: %s%n",
                currentMonth, LocalDateTime.now());

        Files.writeString(finnhubFile, finnhubContent);
        Files.writeString(coingeckoFile, coingeckoContent);

        // Create new service instance (should read from files)
        ApiRequestMeteringService newService = new ApiRequestMeteringService(configDir.toString());

        // Verify it loaded the counts from files
        assertEquals(5, newService.getFinnhubRequestCount());
        assertEquals(3, newService.getCoingeckoRequestCount());
    }

    @Test
    void testInitializationFromOldMonthFiles() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        String oldMonth = "2024-01"; // Old month

        // Create files from previous month
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");

        String finnhubContent = String.format("Month: %s%nCount: 100%nLast Updated: %s%n",
                oldMonth, LocalDateTime.now());
        String coingeckoContent = String.format("Month: %s%nCount: 50%nLast Updated: %s%n",
                oldMonth, LocalDateTime.now());

        Files.writeString(finnhubFile, finnhubContent);
        Files.writeString(coingeckoFile, coingeckoContent);

        // Create new service instance (should preserve old counts until cron job resets)
        ApiRequestMeteringService newService = new ApiRequestMeteringService(configDir.toString());

        // Verify it loaded the counts from files (even though they're from previous month)
        assertEquals(100, newService.getFinnhubRequestCount());
        assertEquals(50, newService.getCoingeckoRequestCount());
    }

    @Test
    void testHandlesMissingConfigDirectory() {
        // Delete config directory if it exists
        Path configDir = tempDir.resolve("config");
        if (Files.exists(configDir)) {
            try {
                Files.delete(configDir);
            } catch (IOException _) {
                // Ignore
            }
        }

        // Service should create directory and work normally
        ApiRequestMeteringService newService = new ApiRequestMeteringService(configDir.toString());
        newService.incrementFinnhubRequests();

        assertEquals(1, newService.getFinnhubRequestCount());
        assertTrue(Files.exists(configDir));
    }

    @Test
    void testHandlesCorruptedFiles() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        // Create corrupted files
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        Files.writeString(finnhubFile, "This is not a valid format");

        // Service should handle gracefully and start with 0
        ApiRequestMeteringService newService = new ApiRequestMeteringService(configDir.toString());
        assertEquals(0, newService.getFinnhubRequestCount());
    }

    @Test
    void testDefaultConstructor() throws IOException {
        // Clean up any existing files first
        Path configDir = Paths.get("config");
        if (Files.exists(configDir)) {
            Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
            Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");
            Files.deleteIfExists(finnhubFile);
            Files.deleteIfExists(coingeckoFile);
        }
        
        // Test the default constructor
        ApiRequestMeteringService defaultService = new ApiRequestMeteringService();
        
        // Should start with 0 counts
        assertEquals(0, defaultService.getFinnhubRequestCount());
        assertEquals(0, defaultService.getCoingeckoRequestCount());
        
        // Should be able to increment
        defaultService.incrementFinnhubRequests();
        defaultService.incrementCoingeckoRequests();
        
        assertEquals(1, defaultService.getFinnhubRequestCount());
        assertEquals(1, defaultService.getCoingeckoRequestCount());
        
        // Clean up after test
        Files.deleteIfExists(configDir.resolve("finnhub-monthly-requests.txt"));
        Files.deleteIfExists(configDir.resolve("coingecko-monthly-requests.txt"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 10;
        final int incrementsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        // Create threads that increment counters concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < incrementsPerThread; j++) {
                    if (threadIndex % 2 == 0) {
                        meteringService.incrementFinnhubRequests();
                    } else {
                        meteringService.incrementCoingeckoRequests();
                    }
                }
            });
        }

        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }

        // Verify final counts
        int expectedFinnhubCount = (numThreads / 2) * incrementsPerThread;
        int expectedCoingeckoCount = (numThreads / 2) * incrementsPerThread;

        assertEquals(expectedFinnhubCount, meteringService.getFinnhubRequestCount());
        assertEquals(expectedCoingeckoCount, meteringService.getCoingeckoRequestCount());
    }

    @Test
    void testReadCounterFromFileWithInvalidFormat() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        // Create file with invalid count format
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String invalidContent = String.format("Month: %s%nCount: invalid_number%nLast Updated: %s%n",
                currentMonth, LocalDateTime.now());
        Files.writeString(finnhubFile, invalidContent);

        // Service should handle gracefully and start with 0
        ApiRequestMeteringService newService = new ApiRequestMeteringService(configDir.toString());
        assertEquals(0, newService.getFinnhubRequestCount());
    }

    @Test
    void testReadCounterFromFileWithIncompleteContent() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        // Create file with incomplete content (only one line)
        Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");
        Files.writeString(coingeckoFile, "Month: 2024-01\n");

        // Service should handle gracefully and start with 0
        ApiRequestMeteringService newService = new ApiRequestMeteringService(configDir.toString());
        assertEquals(0, newService.getCoingeckoRequestCount());
    }

    @Test
    void testPersistCounterIOException() throws IOException {
        // Create a read-only directory to trigger IOException
        Path configDir = tempDir.resolve("readonly-config");
        Files.createDirectories(configDir);
        
        // Make directory read-only (this might not work on all systems, but it's worth trying)
        configDir.toFile().setReadOnly();
        
        try {
            ApiRequestMeteringService readOnlyService = new ApiRequestMeteringService(configDir.toString());
            
            // This should not throw an exception, but should log an error
            readOnlyService.incrementFinnhubRequests();
            
            // Counter should still work in memory
            assertEquals(1, readOnlyService.getFinnhubRequestCount());
        } finally {
            // Restore write permissions for cleanup
            configDir.toFile().setWritable(true);
        }
    }

    @Test
    void testResetCounters() {
        // Add some counts
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        
        assertEquals(2, meteringService.getFinnhubRequestCount());
        assertEquals(1, meteringService.getCoingeckoRequestCount());
        
        // Reset counters
        meteringService.resetCounters();
        
        // Verify counters are reset
        assertEquals(0, meteringService.getFinnhubRequestCount());
        assertEquals(0, meteringService.getCoingeckoRequestCount());
    }

    @Test
    void testGetCurrentMonth() {
        String currentMonth = meteringService.getCurrentMonth();
        String expectedMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        assertEquals(expectedMonth, currentMonth);
    }
}
