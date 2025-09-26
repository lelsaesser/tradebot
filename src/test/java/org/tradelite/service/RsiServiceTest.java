package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.model.RsiDailyClosePrice;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.contains;

@SpringBootTest
class RsiServiceTest {

    @MockitoBean
    private TelegramClient telegramClient;

    @Autowired
    private ObjectMapper objectMapper;

    private RsiService rsiService;

    private final StockSymbol symbol = StockSymbol.AAPL;
    private final String rsiDataFile = "config/rsi-data.json";

    @BeforeEach
    void setUp() throws IOException {
        new File(rsiDataFile).delete();
        rsiService = spy(new RsiService(telegramClient, objectMapper));
    }

    @Test
    void testAddPriceAndCalculateRsi_overbought() throws IOException {
        // Need 15 prices to calculate RSI (14 price changes)
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(14 - i));
        }

        verify(telegramClient, times(1)).sendMessage(anyString());
        verify(rsiService, times(15)).savePriceHistory();
    }

    @Test
    void testAddPriceAndCalculateRsi_oversold() throws IOException {
        // Add declining prices to trigger oversold condition (RSI <= 30)
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 200 - (i * 5), LocalDate.now().minusDays(14 - i));
        }

        // Verify RSI calculation and notification for oversold (may be called multiple times as we add prices)
        verify(telegramClient, atLeastOnce()).sendMessage(contains("🟢"));
        verify(telegramClient, atLeastOnce()).sendMessage(contains("oversold"));
        verify(rsiService, times(15)).savePriceHistory();
    }

    @Test
    void testCalculateRsi_withLosses() {
        // Test RSI calculation with declining prices to cover loss calculation
        java.util.List<Double> decliningPrices = java.util.Arrays.asList(
            100.0, 95.0, 90.0, 85.0, 80.0, 75.0, 70.0, 65.0, 
            60.0, 55.0, 50.0, 45.0, 40.0, 35.0, 30.0
        );
        
        double rsi = rsiService.calculateRsi(decliningPrices);
        
        // RSI should be low for declining prices
        assertThat(rsi, is(closeTo(0.0, 0.1)));
    }

    @Test
    void testCalculateRsi_mixedPrices() {
        // Test RSI calculation with mixed gains and losses to cover RS calculation
        java.util.List<Double> mixedPrices = java.util.Arrays.asList(
            100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0,
            108.0, 114.0, 110.0, 116.0, 112.0, 118.0, 114.0
        );
        
        double rsi = rsiService.calculateRsi(mixedPrices);
        
        // RSI should be between 0 and 100
        assertThat(rsi, is(both(greaterThanOrEqualTo(0.0)).and(lessThanOrEqualTo(100.0))));
    }

    @Test
    void testCalculateRsi_insufficientPrices() {
        // Test that RSI calculation returns 50 with insufficient prices
        java.util.List<Double> insufficientPrices = java.util.Arrays.asList(
            100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0,
            108.0, 114.0, 110.0, 116.0, 112.0 // Only 13 prices
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
        doReturn(expectedHistory).when(spyObjectMapper).readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        RsiService serviceWithHistory = new RsiService(telegramClient, spyObjectMapper);

        assertEquals(1, serviceWithHistory.getPriceHistory().get(symbol.getName()).getPrices().size());
        assertEquals(150.0, serviceWithHistory.getPriceHistory().get(symbol.getName()).getPriceValues().get(0));

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
        doThrow(new java.io.IOException("Invalid file format")).when(spyObjectMapper).readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        assertThrows(IOException.class, () -> new RsiService(telegramClient, spyObjectMapper));

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
            objectMapper.readValue(json, objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, RsiDailyClosePrice.class));
        
        assertEquals(1, deserializedData.get(symbol.getName()).getPrices().size());
        assertEquals(LocalDate.of(2023, 1, 15), deserializedData.get(symbol.getName()).getPrices().getFirst().getDate());
        assertEquals(150.0, deserializedData.get(symbol.getName()).getPrices().getFirst().getPrice());
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
        assertEquals(firstDay, rsiService.getPriceHistory().get(symbol.getName()).getPrices().getFirst().getDate());
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
}
