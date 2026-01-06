package org.tradelite.trading.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PositionTest {

    @Test
    void create_shouldInitializePosition() {
        Position position = Position.create("AAPL", 10.0, 150.0);

        assertThat(position.getSymbol(), is("AAPL"));
        assertThat(position.getQuantity(), is(10.0));
        assertThat(position.getAveragePrice(), is(150.0));
        assertNotNull(position.getFirstPurchaseDate());
        assertNotNull(position.getLastPurchaseDate());
        assertThat(position.getFirstPurchaseDate(), is(equalTo(position.getLastPurchaseDate())));
    }

    @Test
    void addQuantity_shouldUpdateAveragePriceAndQuantity() {
        Position position = Position.create("AAPL", 10.0, 150.0);
        Position updatedPosition = position.addQuantity(10.0, 160.0);

        assertThat(updatedPosition.getQuantity(), is(20.0));
        assertThat(updatedPosition.getAveragePrice(), is(155.0));
        assertThat(updatedPosition.getSymbol(), is("AAPL"));
        assertThat(
                updatedPosition.getFirstPurchaseDate(),
                is(equalTo(position.getFirstPurchaseDate())));
        assertThat(
                updatedPosition.getLastPurchaseDate(),
                is(greaterThan(position.getLastPurchaseDate())));
    }

    @Test
    void getCurrentValue_shouldCalculateCorrectValue() {
        Position position = Position.create("AAPL", 10.0, 150.0);

        assertThat(position.getCurrentValue(160.0), is(1600.0));
        assertThat(position.getCurrentValue(150.0), is(1500.0));
        assertThat(position.getCurrentValue(140.0), is(1400.0));
    }

    @Test
    void getTotalCost_shouldReturnOriginalCost() {
        Position position = Position.create("AAPL", 10.0, 150.0);

        assertThat(position.getTotalCost(), is(1500.0));
    }

    @Test
    void getProfitLoss_shouldCalculateCorrectly() {
        Position position = Position.create("AAPL", 10.0, 150.0);

        assertThat(position.getProfitLoss(160.0), is(100.0));
        assertThat(position.getProfitLoss(150.0), is(0.0));
        assertThat(position.getProfitLoss(140.0), is(-100.0));
    }

    @Test
    void getProfitLossPercentage_shouldCalculateCorrectly() {
        Position position = Position.create("AAPL", 10.0, 150.0);

        assertThat(position.getProfitLossPercentage(165.0), is(closeTo(10.0, 0.01)));
        assertThat(position.getProfitLossPercentage(150.0), is(0.0));
        assertThat(position.getProfitLossPercentage(135.0), is(closeTo(-10.0, 0.01)));
    }

    @Test
    void addQuantity_withDifferentPrices_shouldCalculateCorrectAverage() {
        Position position = Position.create("AAPL", 5.0, 100.0);
        Position updatedPosition = position.addQuantity(10.0, 110.0);

        assertThat(updatedPosition.getQuantity(), is(15.0));
        assertThat(updatedPosition.getAveragePrice(), is(closeTo(106.67, 0.01)));
    }

    @Test
    void jsonConstructor_shouldInitializeCorrectly() {
        LocalDateTime now = LocalDateTime.now();
        Position position = new Position("BTC", 0.5, 50000.0, now, now);

        assertThat(position.getSymbol(), is("BTC"));
        assertThat(position.getQuantity(), is(0.5));
        assertThat(position.getAveragePrice(), is(50000.0));
        assertThat(position.getFirstPurchaseDate(), is(now));
        assertThat(position.getLastPurchaseDate(), is(now));
    }
}
