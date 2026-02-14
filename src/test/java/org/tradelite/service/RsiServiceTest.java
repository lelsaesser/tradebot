package org.tradelite.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.contains;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.service.model.RsiDailyClosePrice;

@SpringBootTest
class RsiServiceTest {

    @MockitoBean private TelegramGateway telegramClient;
    @MockitoBean private FinnhubPriceEvaluator finnhubPriceEvaluator;
    @MockitoBean private CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    @Autowired private ObjectMapper objectMapper;

    private RsiService rsiService;

    private final StockSymbol symbol = new StockSymbol("AAPL", "Apple");
    private final String rsiDataFile = "config/rsi-data.json";

    @BeforeEach
    void setUp() throws IOException {
        new File(rsiDataFile).delete();
        rsiService =
                spy(
                        new RsiService(
                                telegramClient,
                                objectMapper,
                                finnhubPriceEvaluator,
                                coinGeckoPriceEvaluator));
    }

    @Test
    void testAddPriceAndCalculateRsi_overbought() throws IOException {
        // Need 15 prices to calculate RSI (14 price changes)
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(14 - i));
        }

        verify(telegramClient, times(1)).sendMessage(contains(symbol.getDisplayName()));
        verify(rsiService, times(15)).savePriceHistory();
    }

    @Test
    void testAddPriceAndCalculateRsi_oversold() throws IOException {
        // Add declining prices to trigger oversold condition (RSI <= 30)
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 200 - (i * 5), LocalDate.now().minusDays(14 - i));
        }

        // Verify RSI calculation and notification for oversold (may be called multiple times as we
        // add prices)
        verify(telegramClient, atLeastOnce()).sendMessage(contains("🟢"));
        verify(telegramClient, atLeastOnce()).sendMessage(contains("oversold"));
        verify(telegramClient, atLeastOnce()).sendMessage(contains(symbol.getDisplayName()));
        verify(rsiService, times(15)).savePriceHistory();
    }

    @Test
    void testCalculateRsi_withLosses() {
        // Test RSI calculation with declining prices to cover loss calculation
        java.util.List<Double> decliningPrices =
                java.util.Arrays.asList(
                        100.0, 95.0, 90.0, 85.0, 80.0, 75.0, 70.0, 65.0, 60.0, 55.0, 50.0, 45.0,
                        40.0, 35.0, 30.0);

        double rsi = rsiService.calculateRsi(decliningPrices);

        // RSI should be low for declining prices
        assertThat(rsi, is(closeTo(0.0, 0.1)));
    }

    @Test
    void testCalculateRsi_mixedPrices() {
        // Test RSI calculation with mixed gains and losses to cover RS calculation
        java.util.List<Double> mixedPrices =
                java.util.Arrays.asList(
                        100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0, 108.0, 114.0, 110.0,
                        116.0, 112.0, 118.0, 114.0);

        double rsi = rsiService.calculateRsi(mixedPrices);

        // RSI should be between 0 and 100
        assertThat(rsi, is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testCalculateRsi_insufficientPrices() {
        // Test that RSI calculation returns 50 with insufficient prices
        java.util.List<Double> insufficientPrices =
                java.util.Arrays.asList(
                        100.0,
                        105.0,
                        102.0,
                        108.0,
                        104.0,
                        110.0,
                        106.0,
                        112.0,
                        108.0,
                        114.0,
                        110.0,
                        116.0,
                        112.0 // Only 13 prices
                        );

        double rsi = rsiService.calculateRsi(insufficientPrices);
        assertEquals(50, rsi);
    }

    @Test
    void testPriceHistoryPersistence() throws Exception {
        File dummyFile = new File(rsiDataFile);
        dummyFile.getParentFile().mkdirs();
        dummyFile.createNewFile();

        Map<String, RsiDailyClosePrice> expectedHistory = new HashMap<>();
        RsiDailyClosePrice priceData = new RsiDailyClosePrice();
        priceData.addPrice(LocalDate.now(), 150.0);
        expectedHistory.put(symbol.getName(), priceData);

        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doReturn(expectedHistory)
                .when(spyObjectMapper)
                .readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        RsiService serviceWithHistory =
                new RsiService(
                        telegramClient,
                        spyObjectMapper,
                        finnhubPriceEvaluator,
                        coinGeckoPriceEvaluator);

        assertEquals(
                1, serviceWithHistory.getPriceHistory().get(symbol.getName()).getPrices().size());
        assertEquals(
                150.0,
                serviceWithHistory
                        .getPriceHistory()
                        .get(symbol.getName())
                        .getPriceValues()
                        .getFirst());

        dummyFile.delete();
    }

    @Test
    void testLoadPriceHistory_fileNotFound() throws IOException {
        new File(rsiDataFile).delete();

        rsiService.loadPriceHistory();

        assertEquals(0, rsiService.getPriceHistory().size());
    }

    @Test
    void testLoadPriceHistory_invalidFile() throws Exception {
        File dummyFile = new File(rsiDataFile);
        dummyFile.getParentFile().mkdirs();
        dummyFile.createNewFile();

        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doThrow(new java.io.IOException("Invalid file format"))
                .when(spyObjectMapper)
                .readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        assertThrows(
                IOException.class,
                () ->
                        new RsiService(
                                telegramClient,
                                spyObjectMapper,
                                finnhubPriceEvaluator,
                                coinGeckoPriceEvaluator));

        dummyFile.delete();
    }

    @Test
    void testObjectMapperCanSerializeLocalDate() throws Exception {
        // This test ensures the Spring-configured ObjectMapper can handle LocalDate serialization
        // If the JSR310 module is not registered, this test will fail
        RsiDailyClosePrice priceData = new RsiDailyClosePrice();
        priceData.addPrice(LocalDate.of(2023, 1, 15), 150.0);

        Map<String, RsiDailyClosePrice> testData = new HashMap<>();
        testData.put(symbol.getName(), priceData);

        // This should not throw an exception if JSR310 module is properly configured
        String json = objectMapper.writeValueAsString(testData);
        // LocalDate is serialized as an array [year, month, day] by default with JSR310
        assertThat(json, containsString("[2023,1,15]"));

        // Verify we can also deserialize it back
        Map<String, RsiDailyClosePrice> deserializedData =
                objectMapper.readValue(
                        json,
                        objectMapper
                                .getTypeFactory()
                                .constructMapType(
                                        HashMap.class, String.class, RsiDailyClosePrice.class));

        assertEquals(1, deserializedData.get(symbol.getName()).getPrices().size());
        assertEquals(
                LocalDate.of(2023, 1, 15),
                deserializedData.get(symbol.getName()).getPrices().getFirst().getDate());
        assertEquals(
                150.0, deserializedData.get(symbol.getName()).getPrices().getFirst().getPrice());
    }

    @Test
    void testHolidayDetection_identicalPrices() throws IOException {
        LocalDate firstDay = LocalDate.of(2023, 12, 22);
        LocalDate secondDay = LocalDate.of(2023, 12, 25); // Christmas Day
        double price = 150.50;

        // Add first price
        rsiService.addPrice(symbol, price, firstDay);
        assertEquals(1, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());

        // Add identical price on different day - should trigger holiday detection and skip adding
        rsiService.addPrice(symbol, price, secondDay);

        // Verify holiday was detected but price was NOT added (still only 1 entry)
        assertEquals(1, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());

        // Verify the date is still the first day, not the holiday
        assertEquals(
                firstDay,
                rsiService
                        .getPriceHistory()
                        .get(symbol.getName())
                        .getPrices()
                        .getFirst()
                        .getDate());
    }

    @Test
    void testHolidayDetection_slightlyDifferentPrices() throws IOException {
        LocalDate firstDay = LocalDate.of(2023, 12, 22);
        LocalDate secondDay = LocalDate.of(2023, 12, 25);

        // Add first price
        rsiService.addPrice(symbol, 150.50, firstDay);

        // Add slightly different price - should NOT trigger holiday detection
        rsiService.addPrice(symbol, 150.51, secondDay);

        assertEquals(2, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());
    }

    @Test
    void testHolidayDetection_sameDate() throws IOException {
        LocalDate sameDay = LocalDate.of(2023, 12, 22);
        double price = 150.50;

        // Add first price
        rsiService.addPrice(symbol, price, sameDay);

        // Add identical price on same day - should be filtered out as duplicate
        rsiService.addPrice(symbol, price, sameDay);

        assertEquals(1, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());
    }

    @Test
    void testHolidayDetection_noPreviousData() throws IOException {
        LocalDate firstDay = LocalDate.of(2023, 12, 22);
        double price = 150.50;

        // Add first price with no previous data
        rsiService.addPrice(symbol, price, firstDay);

        assertEquals(1, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());
    }

    @Test
    void testHolidayDetection_withinEpsilon() throws IOException {
        LocalDate firstDay = LocalDate.of(2023, 12, 22);
        LocalDate secondDay = LocalDate.of(2023, 12, 25);

        // Add first price
        rsiService.addPrice(symbol, 150.5000, firstDay);

        // Verify we have 1 price entry
        assertEquals(1, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());

        // Add price within epsilon (0.0001) - should trigger holiday detection and skip adding
        rsiService.addPrice(symbol, 150.5000, secondDay);

        // Verify holiday was detected but price was NOT added (still only 1 entry)
        assertEquals(1, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());
    }

    @Test
    void testHolidayDetection_outsideEpsilon() throws IOException {
        LocalDate firstDay = LocalDate.of(2023, 12, 22);
        LocalDate secondDay = LocalDate.of(2023, 12, 25);

        // Add first price
        rsiService.addPrice(symbol, 150.5000, firstDay);

        // Add price outside epsilon (0.0001) - should NOT trigger holiday detection
        rsiService.addPrice(symbol, 150.5002, secondDay);

        assertEquals(2, rsiService.getPriceHistory().get(symbol.getName()).getPrices().size());
    }

    @Test
    void testRsiDiff_isCalculatedAndSent() throws IOException {
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 200 - (i * 5), LocalDate.now().minusDays(15 - i));
        }
        rsiService.getPriceHistory().get(symbol.getName()).setPreviousRsi(10);
        rsiService.addPrice(symbol, 120, LocalDate.now());
        verify(telegramClient, atLeastOnce()).sendMessage(contains("(-"));
    }

    @Test
    void testGetCurrentRsi_withSufficientData() throws IOException {
        // Add 15 prices to have sufficient data for RSI calculation
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(14 - i));
        }

        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isPresent(), is(true));
        assertThat(rsi.get(), is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testGetCurrentRsi_withInsufficientData() throws IOException {
        // Add only a few prices (less than RSI_PERIOD + 1 = 15)
        for (int i = 0; i < 10; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(9 - i));
        }

        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isEmpty(), is(true));
    }

    @Test
    void testGetCurrentRsi_noData() {
        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isEmpty(), is(true));
    }

    @Test
    void testCryptoSymbolDisplayName() throws IOException {
        CoinId cryptoSymbol = CoinId.BITCOIN;

        // Add 15 prices to trigger RSI calculation and notification
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(cryptoSymbol, 100 + i, LocalDate.now().minusDays(14 - i));
        }

        // Verify that crypto symbols use their name directly (no getDisplayName method)
        verify(telegramClient, times(1)).sendMessage(contains(cryptoSymbol.getName()));
        verify(telegramClient, times(1)).sendMessage(contains("bitcoin"));
    }

    @Test
    void testSavePriceHistory_ioException() throws IOException {
        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doThrow(new IOException("Write error"))
                .when(spyObjectMapper)
                .writeValue(any(File.class), any());

        RsiService serviceWithFailingMapper =
                new RsiService(
                        telegramClient,
                        spyObjectMapper,
                        finnhubPriceEvaluator,
                        coinGeckoPriceEvaluator);

        // Adding a price will trigger savePriceHistory, which should throw IOException
        assertThrows(
                IOException.class,
                () -> serviceWithFailingMapper.addPrice(symbol, 100.0, LocalDate.now()));
    }

    @Test
    void testCalculateRsi_averageLossZero() {
        // Test the edge case where avgLoss becomes 0 (all gains, no losses)
        java.util.List<Double> allGainsAfterInitial =
                java.util.Arrays.asList(
                        100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 107.0, 108.0, 109.0, 110.0,
                        111.0, 112.0, 113.0, 114.0);

        double rsi = rsiService.calculateRsi(allGainsAfterInitial);

        // When avgLoss is 0, RSI should be 100
        assertEquals(100.0, rsi, 0.001);
    }

    @Test
    void testGetCurrentPriceFromCache_stockSymbol() {
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 150.0));

        Double price = rsiService.getCurrentPriceFromCache(new StockSymbol("AAPL", "Apple"));

        assertEquals(150.0, price);
    }

    @Test
    void testGetCurrentPriceFromCache_cryptoSymbol() {
        when(coinGeckoPriceEvaluator.getLastPriceCache())
                .thenReturn(Map.of(CoinId.BITCOIN, 50000.0));

        Double price = rsiService.getCurrentPriceFromCache(CoinId.BITCOIN);

        assertEquals(50000.0, price);
    }

    @Test
    void testGetCurrentPriceFromCache_nullCaches() {
        // Test that getCurrentPriceFromCache returns null when caches are empty
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());
        when(coinGeckoPriceEvaluator.getLastPriceCache()).thenReturn(Map.of());

        Double stockPrice = rsiService.getCurrentPriceFromCache(new StockSymbol("AAPL", "Apple"));
        Double cryptoPrice = rsiService.getCurrentPriceFromCache(CoinId.BITCOIN);

        assertThat(stockPrice, is(nullValue()));
        assertThat(cryptoPrice, is(nullValue()));
    }

    @Test
    void testGetCurrentRsi_withCurrentPriceFromCache() throws IOException {
        // Set up historical data (14 prices)
        for (int i = 0; i < 14; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(13 - i));
        }

        // Mock current price from cache
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 125.0));

        var rsi = rsiService.getCurrentRsi(symbol);

        assertThat(rsi.isPresent(), is(true));
        assertThat(rsi.get(), is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testGetCurrentRsi_withCurrentPriceFromCache_crypto() throws IOException {
        CoinId cryptoSymbol = CoinId.BITCOIN;

        // Set up historical data (14 prices)
        for (int i = 0; i < 14; i++) {
            rsiService.addPrice(
                    cryptoSymbol, 40000 + (i * 1000), LocalDate.now().minusDays(13 - i));
        }

        // Mock current price from cache
        when(coinGeckoPriceEvaluator.getLastPriceCache()).thenReturn(Map.of(cryptoSymbol, 55000.0));

        var rsi = rsiService.getCurrentRsi(cryptoSymbol);

        assertThat(rsi.isPresent(), is(true));
        assertThat(rsi.get(), is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testGetCurrentRsi_insufficientDataEvenWithCache() throws IOException {
        // Set up only 10 historical prices (less than RSI_PERIOD)
        for (int i = 0; i < 10; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(9 - i));
        }

        // Mock current price from cache
        when(finnhubPriceEvaluator.getLastPriceCache()).thenReturn(Map.of("AAPL", 125.0));

        var rsi = rsiService.getCurrentRsi(symbol);

        // Even with cached price, we still don't have enough data (only 11 total)
        assertThat(rsi.isEmpty(), is(true));
    }
}
