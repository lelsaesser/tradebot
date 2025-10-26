package org.tradelite.service.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RsiDailyClosePriceTest {

    @Test
    void testAddPrice_newPrice() {
        RsiDailyClosePrice rsiDailyClosePrice = new RsiDailyClosePrice();
        LocalDate date = LocalDate.now();

        rsiDailyClosePrice.addPrice(date, 100.0);

        assertThat(rsiDailyClosePrice.getPrices(), hasSize(1));
        assertThat(rsiDailyClosePrice.getPrices().getFirst().getPrice(), is(equalTo(100.0)));
        assertThat(rsiDailyClosePrice.getPrices().getFirst().getDate(), is(equalTo(date)));
    }

    @Test
    void testAddPrice_updateExistingPrice() {
        RsiDailyClosePrice rsiDailyClosePrice = new RsiDailyClosePrice();
        LocalDate date = LocalDate.now();

        // Add initial price
        rsiDailyClosePrice.addPrice(date, 100.0);
        assertThat(rsiDailyClosePrice.getPrices(), hasSize(1));
        assertThat(rsiDailyClosePrice.getPrices().getFirst().getPrice(), is(equalTo(100.0)));

        // Update price for same date
        rsiDailyClosePrice.addPrice(date, 150.0);
        assertThat(rsiDailyClosePrice.getPrices(), hasSize(1));
        assertThat(rsiDailyClosePrice.getPrices().getFirst().getPrice(), is(equalTo(150.0)));
    }

    @Test
    void testAddPrice_removeOldestWhenSizeExceedsLimit() {
        RsiDailyClosePrice rsiDailyClosePrice = new RsiDailyClosePrice();

        // Add 201 prices to trigger removal of oldest
        for (int i = 0; i < 201; i++) {
            rsiDailyClosePrice.addPrice(LocalDate.now().minusDays(200 - i), 100.0 + i);
        }

        // Should only have 200 prices (oldest removed)
        assertThat(rsiDailyClosePrice.getPrices(), hasSize(200));

        // Verify the oldest price was removed and newest is kept
        List<Double> priceValues = rsiDailyClosePrice.getPriceValues();
        assertThat(priceValues, hasSize(200));
        // The first price (100.0) should be removed, so first value should be 101.0
        assertThat(priceValues.getFirst(), is(equalTo(101.0)));
        assertThat(priceValues.getLast(), is(equalTo(300.0)));
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
