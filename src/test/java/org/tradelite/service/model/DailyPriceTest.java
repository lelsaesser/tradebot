package org.tradelite.service.model;

import org.junit.jupiter.api.Test;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.CoinId;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class DailyPriceTest {

    @Test
    void testConstructorWithThreeFields() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double price = 150.75;
        
        DailyPrice dailyPrice = new DailyPrice("AAPL", date, price);
        
        assertNull(dailyPrice.getId());
        assertEquals("AAPL", dailyPrice.getSymbol());
        assertEquals(date, dailyPrice.getDate());
        assertEquals(price, dailyPrice.getPrice());
        assertNull(dailyPrice.getCreatedAt());
    }

    @Test
    void testDefaultConstructor() {
        DailyPrice dailyPrice = new DailyPrice();
        
        assertNull(dailyPrice.getId());
        assertNull(dailyPrice.getSymbol());
        assertNull(dailyPrice.getDate());
        assertEquals(0.0, dailyPrice.getPrice());
        assertNull(dailyPrice.getCreatedAt());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        LocalDate createdAt = LocalDate.of(2023, 6, 16);
        double price = 100.50;
        
        DailyPrice dailyPrice = new DailyPrice(1L, "TSLA", date, price, createdAt);
        
        assertEquals(1L, dailyPrice.getId());
        assertEquals("TSLA", dailyPrice.getSymbol());
        assertEquals(date, dailyPrice.getDate());
        assertEquals(price, dailyPrice.getPrice());
        assertEquals(createdAt, dailyPrice.getCreatedAt());
    }

    @Test
    void testSettersAndGetters() {
        DailyPrice dailyPrice = new DailyPrice();
        LocalDate date = LocalDate.of(2023, 6, 15);
        LocalDate createdAt = LocalDate.of(2023, 6, 16);
        double price = 100.50;
        
        dailyPrice.setId(1L);
        dailyPrice.setSymbol("TSLA");
        dailyPrice.setDate(date);
        dailyPrice.setPrice(price);
        dailyPrice.setCreatedAt(createdAt);
        
        assertEquals(1L, dailyPrice.getId());
        assertEquals("TSLA", dailyPrice.getSymbol());
        assertEquals(date, dailyPrice.getDate());
        assertEquals(price, dailyPrice.getPrice());
        assertEquals(createdAt, dailyPrice.getCreatedAt());
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        LocalDate createdAt = LocalDate.of(2023, 6, 16);
        double price = 150.75;
        
        DailyPrice dailyPrice1 = new DailyPrice(1L, "AAPL", date, price, createdAt);
        DailyPrice dailyPrice2 = new DailyPrice(1L, "AAPL", date, price, createdAt);
        DailyPrice dailyPrice3 = new DailyPrice(2L, "GOOGL", date, price, createdAt);
        
        assertEquals(dailyPrice1, dailyPrice2);
        assertNotEquals(dailyPrice1, dailyPrice3);
        assertEquals(dailyPrice1.hashCode(), dailyPrice2.hashCode());
        assertNotEquals(dailyPrice1.hashCode(), dailyPrice3.hashCode());
    }

    @Test
    void testEqualsWithNull() {
        DailyPrice dailyPrice = new DailyPrice("AAPL", LocalDate.now(), 100.0);
        
        assertNotEquals(null, dailyPrice);
    }

    @Test
    void testToString() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        LocalDate createdAt = LocalDate.of(2023, 6, 16);
        double price = 150.75;
        DailyPrice dailyPrice = new DailyPrice(1L, "AAPL", date, price, createdAt);
        
        String toString = dailyPrice.toString();
        
        assertNotNull(toString);
        assertTrue(toString.contains("AAPL"));
        assertTrue(toString.contains("150.75"));
        assertTrue(toString.contains("2023-06-15"));
    }

    @Test
    void testWithStockSymbol() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double price = 150.75;
        
        DailyPrice dailyPrice = new DailyPrice(StockSymbol.AAPL.name(), date, price);
        
        assertEquals("AAPL", dailyPrice.getSymbol());
        assertEquals(date, dailyPrice.getDate());
        assertEquals(price, dailyPrice.getPrice());
    }

    @Test
    void testWithCoinId() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double price = 50000.00;
        
        DailyPrice dailyPrice = new DailyPrice(CoinId.BITCOIN.name(), date, price);
        
        assertEquals("BITCOIN", dailyPrice.getSymbol());
        assertEquals(date, dailyPrice.getDate());
        assertEquals(price, dailyPrice.getPrice());
    }

    @Test
    void testPriceHandling() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double price = 150.123456;
        
        DailyPrice dailyPrice = new DailyPrice("AAPL", date, price);
        
        assertEquals(price, dailyPrice.getPrice());
    }

    @Test
    void testDateHandling() {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        double price = 100.00;
        
        DailyPrice todayPrice = new DailyPrice("AAPL", today, price);
        DailyPrice yesterdayPrice = new DailyPrice("AAPL", yesterday, price);
        
        assertEquals(today, todayPrice.getDate());
        assertEquals(yesterday, yesterdayPrice.getDate());
        assertNotEquals(todayPrice.getDate(), yesterdayPrice.getDate());
    }

    @Test
    void testOnCreateCallback() {
        DailyPrice dailyPrice = new DailyPrice("AAPL", LocalDate.now(), 100.0);
        
        // Simulate @PrePersist callback
        dailyPrice.onCreate();
        
        assertNotNull(dailyPrice.getCreatedAt());
        assertEquals(LocalDate.now(), dailyPrice.getCreatedAt());
    }

    @Test
    void testOnCreateCallbackWithExistingCreatedAt() {
        LocalDate existingCreatedAt = LocalDate.of(2023, 1, 1);
        DailyPrice dailyPrice = new DailyPrice(1L, "AAPL", LocalDate.now(), 100.0, existingCreatedAt);
        
        // Simulate @PrePersist callback
        dailyPrice.onCreate();
        
        // Should not change existing createdAt
        assertEquals(existingCreatedAt, dailyPrice.getCreatedAt());
    }

    @Test
    void testNegativePrice() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double negativePrice = -10.50;
        
        DailyPrice dailyPrice = new DailyPrice("TEST", date, negativePrice);
        
        assertEquals(negativePrice, dailyPrice.getPrice());
    }

    @Test
    void testZeroPrice() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double zeroPrice = 0.0;
        
        DailyPrice dailyPrice = new DailyPrice("TEST", date, zeroPrice);
        
        assertEquals(zeroPrice, dailyPrice.getPrice());
    }

    @Test
    void testLargePrice() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double largePrice = 999999.99;
        
        DailyPrice dailyPrice = new DailyPrice("TEST", date, largePrice);
        
        assertEquals(largePrice, dailyPrice.getPrice());
    }

    @Test
    void testSymbolLength() {
        LocalDate date = LocalDate.of(2023, 6, 15);
        double price = 100.0;
        String longSymbol = "VERYLONGSYMBOLNAME";
        
        DailyPrice dailyPrice = new DailyPrice(longSymbol, date, price);
        
        assertEquals(longSymbol, dailyPrice.getSymbol());
    }
}
