package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.model.RsiDailyClosePrice;

import java.io.File;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class RsiServiceTest {

    @Mock
    private TelegramClient telegramClient;

    private ObjectMapper objectMapper = new ObjectMapper();

    private RsiService rsiService;

    private final StockSymbol symbol = StockSymbol.AAPL;
    private final String rsiDataFile = "config/rsi-data.json";

    @BeforeEach
    void setUp() {
        // Ensure the test starts with a clean state
        new File(rsiDataFile).delete();
        rsiService = new RsiService(telegramClient, new ObjectMapper());
    }

    @Test
    void testAddPriceAndCalculateRsi() {
        for (int i = 0; i < 14; i++) {
            rsiService.addPrice(symbol, 100 + i, LocalDate.now().minusDays(13 - i));
        }

        // Verify RSI calculation and notification
        verify(telegramClient, times(1)).sendMessage(anyString());
    }

    @Test
    void testPriceHistoryPersistence() throws Exception {
        // Create a dummy file to make file.exists() true
        File dummyFile = new File(rsiDataFile);
        dummyFile.getParentFile().mkdirs();
        dummyFile.createNewFile();

        // Create the data that should be "loaded"
        Map<org.tradelite.common.TickerSymbol, RsiDailyClosePrice> expectedHistory = new HashMap<>();
        RsiDailyClosePrice priceData = new RsiDailyClosePrice();
        priceData.addPrice(LocalDate.now(), 150.0);
        expectedHistory.put(symbol, priceData);

        // Mock objectMapper to return the data when readValue is called
        ObjectMapper spyObjectMapper = spy(new ObjectMapper());
        doReturn(expectedHistory).when(spyObjectMapper).readValue(any(File.class), any(com.fasterxml.jackson.databind.JavaType.class));

        // Create the service. Its constructor will call loadPriceHistory.
        RsiService serviceWithHistory = new RsiService(telegramClient, spyObjectMapper);

        // Assert that the history was loaded correctly.
        assertEquals(1, serviceWithHistory.getPriceHistory().get(symbol).getPrices().size());
        assertEquals(150.0, serviceWithHistory.getPriceHistory().get(symbol).getPriceValues().get(0));

        // Clean up the dummy file
        dummyFile.delete();
    }

    @Test
    void testLoadPriceHistory_fileNotFound() {
        // Ensure the file does not exist
        new File(rsiDataFile).delete();

        // This should not throw an exception
        rsiService.loadPriceHistory();

        assertEquals(0, rsiService.getPriceHistory().size());
    }
}
