package org.tradelite.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiRequestMeteringServiceTest {

    @TempDir Path tempDir;

    private ApiRequestMeteringService meteringService;
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @AfterAll
    static void cleanUp() throws IOException {
        Path configDir = Paths.get("config");
        Files.deleteIfExists(configDir.resolve("finnhub-monthly-requests.txt"));
        Files.deleteIfExists(configDir.resolve("coingecko-monthly-requests.txt"));
        Files.deleteIfExists(configDir.resolve("twelvedata-monthly-requests.txt"));
    }

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
    void testIncrementTwelveDataRequests() {
        assertEquals(0, meteringService.getTwelveDataRequestCount());

        meteringService.incrementTwelveDataRequests();
        assertEquals(1, meteringService.getTwelveDataRequestCount());

        meteringService.incrementTwelveDataRequests();
        assertEquals(2, meteringService.getTwelveDataRequestCount());
    }

    @Test
    void testBothCountersIndependent() {
        // Increment Finnhub
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();

        // Increment CoinGecko
        meteringService.incrementCoingeckoRequests();

        // Increment TwelveData
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();

        // Verify they are independent
        assertEquals(2, meteringService.getFinnhubRequestCount());
        assertEquals(1, meteringService.getCoingeckoRequestCount());
        assertEquals(3, meteringService.getTwelveDataRequestCount());
    }

    @Test
    void testFilePersistence() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        // Increment counters
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();

        // Check that files are created
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");
        Path twelvedataFile = configDir.resolve("twelvedata-monthly-requests.txt");

        assertTrue(Files.exists(finnhubFile));
        assertTrue(Files.exists(coingeckoFile));
        assertTrue(Files.exists(twelvedataFile));

        // Check file contents
        String finnhubContent = Files.readString(finnhubFile);
        String coingeckoContent = Files.readString(coingeckoFile);
        String twelvedataContent = Files.readString(twelvedataFile);

        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);
        assertTrue(finnhubContent.contains("Month: " + currentMonth));
        assertTrue(finnhubContent.contains("Count: 1"));
        assertTrue(coingeckoContent.contains("Month: " + currentMonth));
        assertTrue(coingeckoContent.contains("Count: 2"));
        assertTrue(twelvedataContent.contains("Month: " + currentMonth));
        assertTrue(twelvedataContent.contains("Count: 3"));
    }

    @Test
    void testGetRequestCountSummary() {
        meteringService.incrementFinnhubRequests();
        meteringService.incrementFinnhubRequests();
        meteringService.incrementCoingeckoRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();

        String summary = meteringService.getRequestCountSummary();
        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);

        assertTrue(summary.contains(currentMonth));
        assertTrue(summary.contains("Finnhub: 2"));
        assertTrue(summary.contains("CoinGecko: 1"));
        assertTrue(summary.contains("TwelveData: 3"));
    }

    @Test
    void testInitializationFromExistingFiles() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        String currentMonth = LocalDateTime.now().format(MONTH_FORMATTER);

        // Create existing files with some counts
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");
        Path twelvedataFile = configDir.resolve("twelvedata-monthly-requests.txt");

        String finnhubContent =
                String.format(
                        "Month: %s%nCount: 5%nLast Updated: %s%n",
                        currentMonth, LocalDateTime.now());
        String coingeckoContent =
                String.format(
                        "Month: %s%nCount: 3%nLast Updated: %s%n",
                        currentMonth, LocalDateTime.now());
        String twelvedataContent =
                String.format(
                        "Month: %s%nCount: 7%nLast Updated: %s%n",
                        currentMonth, LocalDateTime.now());

        Files.writeString(finnhubFile, finnhubContent);
        Files.writeString(coingeckoFile, coingeckoContent);
        Files.writeString(twelvedataFile, twelvedataContent);

        // Create new service instance (should read from files)
        ApiRequestMeteringService newService = new ApiRequestMeteringService(configDir.toString());

        // Verify it loaded the counts from files
        assertEquals(5, newService.getFinnhubRequestCount());
        assertEquals(3, newService.getCoingeckoRequestCount());
        assertEquals(7, newService.getTwelveDataRequestCount());
    }

    @Test
    void testInitializationFromOldMonthFiles() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        String oldMonth = "2024-01"; // Old month

        // Create files from previous month
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        Path coingeckoFile = configDir.resolve("coingecko-monthly-requests.txt");

        String finnhubContent =
                String.format(
                        "Month: %s%nCount: 100%nLast Updated: %s%n", oldMonth, LocalDateTime.now());
        String coingeckoContent =
                String.format(
                        "Month: %s%nCount: 50%nLast Updated: %s%n", oldMonth, LocalDateTime.now());

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
            Path twelvedataFile = configDir.resolve("twelvedata-monthly-requests.txt");
            Files.deleteIfExists(finnhubFile);
            Files.deleteIfExists(coingeckoFile);
            Files.deleteIfExists(twelvedataFile);
        }

        // Test with the default counter directory
        ApiRequestMeteringService defaultService = new ApiRequestMeteringService("config");

        // Should start with 0 counts
        assertEquals(0, defaultService.getFinnhubRequestCount());
        assertEquals(0, defaultService.getCoingeckoRequestCount());
        assertEquals(0, defaultService.getTwelveDataRequestCount());

        // Should be able to increment
        defaultService.incrementFinnhubRequests();
        defaultService.incrementCoingeckoRequests();
        defaultService.incrementTwelveDataRequests();

        assertEquals(1, defaultService.getFinnhubRequestCount());
        assertEquals(1, defaultService.getCoingeckoRequestCount());
        assertEquals(1, defaultService.getTwelveDataRequestCount());

        // Clean up after test
        Files.deleteIfExists(configDir.resolve("finnhub-monthly-requests.txt"));
        Files.deleteIfExists(configDir.resolve("coingecko-monthly-requests.txt"));
        Files.deleteIfExists(configDir.resolve("twelvedata-monthly-requests.txt"));
    }

    @Test
    void testConcurrentAccess() throws InterruptedException {
        final int numThreads = 12;
        final int incrementsPerThread = 100;
        Thread[] threads = new Thread[numThreads];

        // Create threads that increment counters concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] =
                    new Thread(
                            () -> {
                                for (int j = 0; j < incrementsPerThread; j++) {
                                    if (threadIndex % 3 == 0) {
                                        meteringService.incrementFinnhubRequests();
                                    } else if (threadIndex % 3 == 1) {
                                        meteringService.incrementCoingeckoRequests();
                                    } else {
                                        meteringService.incrementTwelveDataRequests();
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
        int expectedFinnhubCount = (numThreads / 3) * incrementsPerThread;
        int expectedCoingeckoCount = (numThreads / 3) * incrementsPerThread;
        int expectedTwelveDataCount = (numThreads / 3) * incrementsPerThread;

        assertEquals(expectedFinnhubCount, meteringService.getFinnhubRequestCount());
        assertEquals(expectedCoingeckoCount, meteringService.getCoingeckoRequestCount());
        assertEquals(expectedTwelveDataCount, meteringService.getTwelveDataRequestCount());
    }

    @Test
    void testReadCounterFromFileWithInvalidFormat() throws IOException {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);

        // Create file with invalid count format
        Path finnhubFile = configDir.resolve("finnhub-monthly-requests.txt");
        String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        String invalidContent =
                String.format(
                        "Month: %s%nCount: invalid_number%nLast Updated: %s%n",
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
            ApiRequestMeteringService readOnlyService =
                    new ApiRequestMeteringService(configDir.toString());

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
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();
        meteringService.incrementTwelveDataRequests();

        assertEquals(2, meteringService.getFinnhubRequestCount());
        assertEquals(1, meteringService.getCoingeckoRequestCount());
        assertEquals(3, meteringService.getTwelveDataRequestCount());

        // Reset counters
        meteringService.resetCounters();

        // Verify counters are reset
        assertEquals(0, meteringService.getFinnhubRequestCount());
        assertEquals(0, meteringService.getCoingeckoRequestCount());
        assertEquals(0, meteringService.getTwelveDataRequestCount());
    }

    @Test
    void testGetCurrentMonth() {
        String currentMonth = meteringService.getCurrentMonth();
        String expectedMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
        assertEquals(expectedMonth, currentMonth);
    }
}
