package org.tradelite.service.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class RsiDailyClosePriceTest {

    @Test
    void testAddPrice_newPrice() {
        RsiDailyClosePrice rsiDailyClosePrice = new RsiDailyClosePrice();
        LocalDate date = LocalDate.now();
        
        rsiDailyClosePrice.addPrice(date, 100.0);
        
        assertThat(rsiDailyClosePrice.getPrices(), hasSize(1));
        assertThat(rsiDailyClosePrice.getPrices().get(0).getPrice(), is(equalTo(100.0)));
        assertThat(rsiDailyClosePrice.getPrices().get(0).getDate(), is(equalTo(date)));
    }

    @Test
    void testAddPrice_updateExistingPrice() {
        RsiDailyClosePrice rsiDailyClosePrice = new RsiDailyClosePrice();
        LocalDate date = LocalDate.now();
        
        // Add initial price
        rsiDailyClosePrice.addPrice(date, 100.0);
        assertThat(rsiDailyClosePrice.getPrices(), hasSize(1));
        assertThat(rsiDailyClosePrice.getPrices().get(0).getPrice(), is(equalTo(100.0)));
        
        // Update price for same date
        rsiDailyClosePrice.addPrice(date, 150.0);
        assertThat(rsiDailyClosePrice.getPrices(), hasSize(1));
        assertThat(rsiDailyClosePrice.getPrices().get(0).getPrice(), is(equalTo(150.0)));
    }

    @Test
    void testAddPrice_removeOldestWhenSizeExceeds14() {
        RsiDailyClosePrice rsiDailyClosePrice = new RsiDailyClosePrice();
        
        // Add 15 prices to trigger removal of oldest
        for (int i = 0; i < 15; i++) {
            rsiDailyClosePrice.addPrice(LocalDate.now().minusDays(14 - i), 100.0 + i);
        }
        
        // Should only have 14 prices (oldest removed)
        assertThat(rsiDailyClosePrice.getPrices(), hasSize(14));
        
        // Verify the oldest price was removed and newest is kept
        List<Double> priceValues = rsiDailyClosePrice.getPriceValues();
        assertThat(priceValues, hasSize(14));
        // The first price (100.0) should be removed, so first value should be 101.0
        assertThat(priceValues.get(0), is(equalTo(101.0)));
        assertThat(priceValues.get(13), is(equalTo(114.0))); // Last price should be 114.0
    }

    @Test
    void testGetPriceValues_sortedByDate() {
        RsiDailyClosePrice rsiDailyClosePrice = new RsiDailyClosePrice();
        
        // Add prices in random order
        rsiDailyClosePrice.addPrice(LocalDate.now().minusDays(2), 102.0);
        rsiDailyClosePrice.addPrice(LocalDate.now().minusDays(0), 104.0);
        rsiDailyClosePrice.addPrice(LocalDate.now().minusDays(1), 103.0);
        rsiDailyClosePrice.addPrice(LocalDate.now().minusDays(3), 101.0);
        
        List<Double> priceValues = rsiDailyClosePrice.getPriceValues();
        
        // Should be sorted by date (oldest first)
        assertThat(priceValues, hasSize(4));
        assertThat(priceValues.get(0), is(equalTo(101.0))); // 3 days ago
        assertThat(priceValues.get(1), is(equalTo(102.0))); // 2 days ago
        assertThat(priceValues.get(2), is(equalTo(103.0))); // 1 day ago
        assertThat(priceValues.get(3), is(equalTo(104.0))); // today
    }
}
