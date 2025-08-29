package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
class RsiServiceTest {

    @Mock
    private TelegramClient telegramClient;

    private RsiService rsiService;

    private final StockSymbol symbol = StockSymbol.AAPL;
    private final String rsiDataFile = "config/rsi-data.json";

    @BeforeEach
    void setUp() throws IOException {
        new File(rsiDataFile).delete();
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        rsiService = new RsiService(telegramClient, objectMapper);
    }

    @Test
    void testAddPriceAndCalculateRsi_overbought() throws IOException {
        // Need 15 prices to calculate RSI (14 price changes)
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(14 - i));
        }

        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    @Test
    void testAddPriceAndCalculateRsi_oversold() throws IOException {
        // Add declining prices to trigger oversold condition (RSI <= 30)
        for (int i = 0; i < 15; i++) {
            rsiService.addPrice(symbol, 200 - (i * 5), LocalDate.now().minusDays(14 - i));
        }

        // Verify RSI calculation and notification for oversold (may be called multiple times as we add prices)
        verify(telegramClient, atLeastOnce()).sendMessage(contains("oversold"));
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
        // Test that RSI calculation throws exception with insufficient prices
        java.util.List<Double> insufficientPrices = java.util.Arrays.asList(
            100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0,
            108.0, 114.0, 110.0, 116.0, 112.0, 118.0 // Only 14 prices
        );
        
        assertThrows(IllegalArgumentException.class, () -> {
            rsiService.calculateRsi(insufficientPrices);
        });
    }

    @Test
    void testPriceHistoryPersistence() throws Exception {
        File dummyFile = new File(rsiDataFile);
        dummyFile.getParentFile().mkdirs();
        dummyFile.createNewFile();

        Map<org.tradelite.common.TickerSymbol, RsiDailyClosePrice> expectedHistory = new HashMap<>();
        RsiDailyClosePrice priceData = new RsiDailyClosePrice();
        priceData.addPrice(LocalDate.now(), 150.0);
        expectedHistory.put(symbol, priceData);

        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doReturn(expectedHistory).when(spyObjectMapper).readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        RsiService serviceWithHistory = new RsiService(telegramClient, spyObjectMapper);

        assertEquals(1, serviceWithHistory.getPriceHistory().get(symbol).getPrices().size());
        assertEquals(150.0, serviceWithHistory.getPriceHistory().get(symbol).getPriceValues().get(0));

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
}
