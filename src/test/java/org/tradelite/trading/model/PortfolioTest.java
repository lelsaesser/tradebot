package org.tradelite.trading.model;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PortfolioTest {

    @Test
    void createInitial_shouldInitializeWithBalance() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);

        assertThat(portfolio.getCashBalance(), is(100000.0));
        assertThat(portfolio.getPositions(), is(anEmptyMap()));
    }

    @Test
    void buy_shouldCreateNewPosition() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio updated = portfolio.buy("AAPL", 10.0, 150.0);

        assertThat(updated.getCashBalance(), is(98500.0));
        assertThat(updated.hasPosition("AAPL"), is(true));
        Position position = updated.getPosition("AAPL");
        assertThat(position.getQuantity(), is(10.0));
        assertThat(position.getAveragePrice(), is(150.0));
    }

    @Test
    void buy_shouldAddToExistingPosition() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPosition = portfolio.buy("AAPL", 10.0, 150.0);
        Portfolio updated = withPosition.buy("AAPL", 10.0, 160.0);

        assertThat(updated.getCashBalance(), is(96900.0));
        Position position = updated.getPosition("AAPL");
        assertThat(position.getQuantity(), is(20.0));
        assertThat(position.getAveragePrice(), is(155.0));
    }

    @Test
    void buy_shouldThrowExceptionWhenInsufficientFunds() {
        Portfolio portfolio = Portfolio.createInitial(100.0);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class, () -> portfolio.buy("AAPL", 10.0, 150.0));

        assertThat(exception.getMessage(), containsString("Insufficient funds"));
    }

    @Test
    void sell_shouldRemovePositionAndAddCash() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPosition = portfolio.buy("AAPL", 10.0, 150.0);
        Portfolio updated = withPosition.sell("AAPL", 160.0);

        assertThat(updated.getCashBalance(), is(100100.0));
        assertThat(updated.hasPosition("AAPL"), is(false));
    }

    @Test
    void sell_shouldThrowExceptionWhenNoPosition() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);

        IllegalArgumentException exception =
                assertThrows(IllegalArgumentException.class, () -> portfolio.sell("AAPL", 150.0));

        assertThat(exception.getMessage(), containsString("No position found"));
    }

    @Test
    void getTotalValue_shouldCalculateCorrectly() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPositions = portfolio.buy("AAPL", 10.0, 150.0).buy("GOOG", 5.0, 2000.0);

        Map<String, Double> currentPrices = new HashMap<>();
        currentPrices.put("AAPL", 160.0);
        currentPrices.put("GOOG", 2100.0);

        double totalValue = withPositions.getTotalValue(currentPrices);
        assertThat(totalValue, is(closeTo(100600.0, 0.01)));
    }

    @Test
    void getTotalProfitLoss_shouldCalculateCorrectly() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPositions = portfolio.buy("AAPL", 10.0, 150.0).buy("GOOG", 5.0, 2000.0);

        Map<String, Double> currentPrices = new HashMap<>();
        currentPrices.put("AAPL", 160.0);
        currentPrices.put("GOOG", 2100.0);

        double profitLoss = withPositions.getTotalProfitLoss(currentPrices);
        assertThat(profitLoss, is(closeTo(600.0, 0.01)));
    }

    @Test
    void getTotalValue_withMissingPrice_shouldUseFallback() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPosition = portfolio.buy("AAPL", 10.0, 150.0);

        Map<String, Double> currentPrices = new HashMap<>();

        double totalValue = withPosition.getTotalValue(currentPrices);
        assertThat(totalValue, is(closeTo(100000.0, 0.01)));
    }

    @Test
    void hasPosition_shouldReturnCorrectStatus() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPosition = portfolio.buy("AAPL", 10.0, 150.0);

        assertThat(withPosition.hasPosition("AAPL"), is(true));
        assertThat(withPosition.hasPosition("GOOG"), is(false));
    }

    @Test
    void getPosition_shouldReturnPosition() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);
        Portfolio withPosition = portfolio.buy("AAPL", 10.0, 150.0);

        Position position = withPosition.getPosition("AAPL");
        assertNotNull(position);
        assertThat(position.getSymbol(), is("AAPL"));
    }

    @Test
    void getPosition_shouldReturnNullWhenNotExists() {
        Portfolio portfolio = Portfolio.createInitial(100000.0);

        Position position = portfolio.getPosition("AAPL");
        assertNull(position);
    }

    @Test
    void jsonConstructor_shouldInitializeCorrectly() {
        Map<String, Position> positions = new HashMap<>();
        positions.put("AAPL", Position.create("AAPL", 10.0, 150.0));

        Portfolio portfolio = new Portfolio(50000.0, positions);

        assertThat(portfolio.getCashBalance(), is(50000.0));
        assertThat(portfolio.getPositions().size(), is(1));
        assertThat(portfolio.hasPosition("AAPL"), is(true));
    }

    @Test
    void jsonConstructor_withNullPositions_shouldInitializeEmpty() {
        Portfolio portfolio = new Portfolio(50000.0, null);

        assertThat(portfolio.getCashBalance(), is(50000.0));
        assertThat(portfolio.getPositions(), is(anEmptyMap()));
    }
}
