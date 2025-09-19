package org.tradelite.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.repository.DailyPriceRepository;
import org.tradelite.service.model.DailyPrice;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class RsiServiceTest {

    @MockitoBean
    private TelegramClient telegramClient;

    @MockitoBean
    private DailyPriceRepository dailyPriceRepository;

    @MockitoBean
    private DataMigrationService dataMigrationService;

    private RsiService rsiService;

    private final StockSymbol symbol = StockSymbol.AAPL;

    @BeforeEach
    void setUp() {
        // Mock migration service behavior
        when(dataMigrationService.isMigrationNeeded()).thenReturn(false);
        when(dataMigrationService.getMigrationStatus()).thenReturn("Test status");
        
        rsiService = new RsiService(telegramClient, dailyPriceRepository, dataMigrationService);
    }

    @Test
    void testAddPriceAndCalculateRsi_overbought() {
        // Mock no existing price
        when(dailyPriceRepository.findBySymbolAndDate(anyString(), any(LocalDate.class)))
            .thenReturn(Optional.empty());
        
        // Mock price count for cleanup
        when(dailyPriceRepository.countBySymbol(anyString())).thenReturn(1L);
        
        // Mock prices for RSI calculation (15 increasing prices)
        List<DailyPrice> mockPrices = createMockPrices(symbol.getName(), 100.0, 15, true);
        when(dailyPriceRepository.findLatest200PricesBySymbol(symbol.getName()))
            .thenReturn(mockPrices);

        // Add a price that should trigger RSI calculation
        rsiService.addPrice(symbol, 115.0, LocalDate.now());

        // Verify price was saved
        verify(dailyPriceRepository, times(1)).save(any(DailyPrice.class));
        
        // Verify RSI notification was sent for overbought condition
        verify(telegramClient, times(1)).sendMessage(contains("overbought"));
    }

    @Test
    void testAddPriceAndCalculateRsi_oversold() {
        // Mock no existing price
        when(dailyPriceRepository.findBySymbolAndDate(anyString(), any(LocalDate.class)))
            .thenReturn(Optional.empty());
        
        // Mock price count for cleanup
        when(dailyPriceRepository.countBySymbol(anyString())).thenReturn(1L);
        
        // Mock prices for RSI calculation (15 decreasing prices)
        List<DailyPrice> mockPrices = createMockPrices(symbol.getName(), 200.0, 15, false);
        when(dailyPriceRepository.findLatest200PricesBySymbol(symbol.getName()))
            .thenReturn(mockPrices);

        // Add a price that should trigger RSI calculation
        rsiService.addPrice(symbol, 130.0, LocalDate.now());

        // Verify price was saved
        verify(dailyPriceRepository, times(1)).save(any(DailyPrice.class));
        
        // Verify RSI notification was sent for oversold condition
        verify(telegramClient, times(1)).sendMessage(contains("oversold"));
    }

    @Test
    void testCalculateRsi_withLosses() {
        // Test RSI calculation with declining prices
        List<Double> decliningPrices = Arrays.asList(
            100.0, 95.0, 90.0, 85.0, 80.0, 75.0, 70.0, 65.0, 
            60.0, 55.0, 50.0, 45.0, 40.0, 35.0, 30.0
        );
        
        double rsi = rsiService.calculateRsi(decliningPrices);
        
        // RSI should be low for declining prices
        assertThat(rsi, is(closeTo(0.0, 10.0)));
    }

    @Test
    void testCalculateRsi_mixedPrices() {
        // Test RSI calculation with mixed gains and losses
        List<Double> mixedPrices = Arrays.asList(
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
        List<Double> insufficientPrices = Arrays.asList(
            100.0, 105.0, 102.0, 108.0, 104.0, 110.0, 106.0, 112.0,
            108.0, 114.0, 110.0, 116.0, 112.0 // Only 13 prices
        );

        double rsi = rsiService.calculateRsi(insufficientPrices);
        assertEquals(50, rsi);
    }

    @Test
    void testHolidayDetection_identicalPrices() {
        LocalDate firstDay = LocalDate.of(2023, 12, 22);
        LocalDate secondDay = LocalDate.of(2023, 12, 25); // Christmas Day
        double price = 150.50;

        // Mock existing price for holiday detection
        List<DailyPrice> existingPrices = List.of(
            new DailyPrice(symbol.getName(), firstDay, price)
        );
        when(dailyPriceRepository.findBySymbolOrderByDateDesc(symbol.getName()))
            .thenReturn(existingPrices);

        // Mock no existing price for the new date
        when(dailyPriceRepository.findBySymbolAndDate(symbol.getName(), secondDay))
            .thenReturn(Optional.empty());

        // Add identical price on different day - should trigger holiday detection and skip adding
        rsiService.addPrice(symbol, price, secondDay);

        // Verify that save was NOT called due to holiday detection
        verify(dailyPriceRepository, never()).save(any(DailyPrice.class));
    }

    @Test
    void testHolidayDetection_slightlyDifferentPrices() {
        LocalDate firstDay = LocalDate.of(2023, 12, 22);
        LocalDate secondDay = LocalDate.of(2023, 12, 25);
        
        // Mock existing price for holiday detection
        List<DailyPrice> existingPrices = List.of(
            new DailyPrice(symbol.getName(), firstDay, 150.50)
        );
        when(dailyPriceRepository.findBySymbolOrderByDateDesc(symbol.getName()))
            .thenReturn(existingPrices);

        // Mock no existing price for the new date
        when(dailyPriceRepository.findBySymbolAndDate(symbol.getName(), secondDay))
            .thenReturn(Optional.empty());
        
        // Mock price count for cleanup
        when(dailyPriceRepository.countBySymbol(anyString())).thenReturn(1L);

        // Add slightly different price - should NOT trigger holiday detection
        rsiService.addPrice(symbol, 150.51, secondDay);

        // Verify that save WAS called since prices are different enough
        verify(dailyPriceRepository, times(1)).save(any(DailyPrice.class));
    }

    @Test
    void testAddPrice_updateExisting() {
        LocalDate date = LocalDate.of(2023, 12, 22);
        double oldPrice = 150.50;
        double newPrice = 151.00;

        // Mock existing price
        DailyPrice existingPrice = new DailyPrice(symbol.getName(), date, oldPrice);
        when(dailyPriceRepository.findBySymbolAndDate(symbol.getName(), date))
            .thenReturn(Optional.of(existingPrice));
        
        // Mock price count for cleanup
        when(dailyPriceRepository.countBySymbol(anyString())).thenReturn(1L);

        // Add new price for same date - should update existing
        rsiService.addPrice(symbol, newPrice, date);

        // Verify that save was called with updated price
        verify(dailyPriceRepository, times(1)).save(argThat(price -> 
            price.getPrice() == newPrice && price.getDate().equals(date)
        ));
    }

    @Test
    void testCleanupOldPrices() {
        LocalDate date = LocalDate.now();
        
        // Mock no existing price for the date
        when(dailyPriceRepository.findBySymbolAndDate(symbol.getName(), date))
            .thenReturn(Optional.empty());
        
        // Mock empty prices list for holiday detection
        when(dailyPriceRepository.findBySymbolOrderByDateDesc(symbol.getName()))
            .thenReturn(List.of()) // Empty list first for holiday detection
            .thenReturn(createMockPrices(symbol.getName(), 100.0, 250, true)); // Then full list for cleanup
        
        // Mock price count exceeding limit
        when(dailyPriceRepository.countBySymbol(symbol.getName())).thenReturn(250L);

        rsiService.addPrice(symbol, 100.0, date);

        // Verify cleanup was called
        verify(dailyPriceRepository, times(1)).deleteAll(anyList());
    }

    @Test
    void testGetAllSymbolsWithPriceData() {
        List<String> expectedSymbols = Arrays.asList("AAPL", "GOOGL", "MSFT");
        when(dailyPriceRepository.findAllUniqueSymbols()).thenReturn(expectedSymbols);

        List<String> result = rsiService.getAllSymbolsWithPriceData();

        assertEquals(expectedSymbols, result);
        verify(dailyPriceRepository, times(1)).findAllUniqueSymbols();
    }

    @Test
    void testGetPriceHistory() {
        List<DailyPrice> expectedPrices = createMockPrices(symbol.getName(), 100.0, 5, true);
        when(dailyPriceRepository.findBySymbolOrderByDateAsc(symbol.getName()))
            .thenReturn(expectedPrices);

        List<DailyPrice> result = rsiService.getPriceHistory(symbol.getName());

        assertEquals(expectedPrices, result);
        verify(dailyPriceRepository, times(1)).findBySymbolOrderByDateAsc(symbol.getName());
    }

    @Test
    void testGetLatestPrices() {
        List<DailyPrice> allPrices = createMockPrices(symbol.getName(), 100.0, 10, true);
        when(dailyPriceRepository.findBySymbolOrderByDateDesc(symbol.getName()))
            .thenReturn(allPrices);

        List<DailyPrice> result = rsiService.getLatestPrices(symbol.getName(), 5);

        assertEquals(5, result.size());
        verify(dailyPriceRepository, times(1)).findBySymbolOrderByDateDesc(symbol.getName());
    }

    /**
     * Helper method to create mock price data
     */
    private List<DailyPrice> createMockPrices(String symbol, double startPrice, int count, boolean increasing) {
        List<DailyPrice> prices = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double price = increasing ? startPrice + i : startPrice - i;
            LocalDate date = LocalDate.now().minusDays(count - i - 1);
            prices.add(new DailyPrice(symbol, date, price));
        }
        return prices;
    }
}
