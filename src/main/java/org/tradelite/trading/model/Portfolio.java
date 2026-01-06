package org.tradelite.trading.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class Portfolio {

    private final double cashBalance;
    private final Map<String, Position> positions;

    @JsonCreator
    public Portfolio(
            @JsonProperty("cashBalance") double cashBalance,
            @JsonProperty("positions") Map<String, Position> positions) {
        this.cashBalance = cashBalance;
        this.positions = positions != null ? new HashMap<>(positions) : new HashMap<>();
    }

    public static Portfolio createInitial(double initialBalance) {
        return new Portfolio(initialBalance, new HashMap<>());
    }

    public Portfolio buy(String symbol, double quantity, double price) {
        double cost = quantity * price;
        if (cost > cashBalance) {
            throw new IllegalArgumentException(
                    "Insufficient funds. Required: " + cost + ", Available: " + cashBalance);
        }

        Map<String, Position> newPositions = new HashMap<>(positions);
        Position existingPosition = newPositions.get(symbol);

        if (existingPosition != null) {
            newPositions.put(symbol, existingPosition.addQuantity(quantity, price));
        } else {
            newPositions.put(symbol, Position.create(symbol, quantity, price));
        }

        return new Portfolio(cashBalance - cost, newPositions);
    }

    public Portfolio sell(String symbol, double currentPrice) {
        Position position = positions.get(symbol);
        if (position == null) {
            throw new IllegalArgumentException("No position found for symbol: " + symbol);
        }

        double proceeds = position.getCurrentValue(currentPrice);
        Map<String, Position> newPositions = new HashMap<>(positions);
        newPositions.remove(symbol);

        return new Portfolio(cashBalance + proceeds, newPositions);
    }

    public boolean hasPosition(String symbol) {
        return positions.containsKey(symbol);
    }

    public Position getPosition(String symbol) {
        return positions.get(symbol);
    }

    public double getTotalValue(Map<String, Double> currentPrices) {
        double positionsValue =
                positions.values().stream()
                        .mapToDouble(
                                position ->
                                        position.getCurrentValue(
                                                currentPrices.getOrDefault(
                                                        position.getSymbol(),
                                                        position.getAveragePrice())))
                        .sum();
        return cashBalance + positionsValue;
    }

    public double getTotalProfitLoss(Map<String, Double> currentPrices) {
        double totalCost = positions.values().stream().mapToDouble(Position::getTotalCost).sum();
        double totalCurrentValue =
                positions.values().stream()
                        .mapToDouble(
                                position ->
                                        position.getCurrentValue(
                                                currentPrices.getOrDefault(
                                                        position.getSymbol(),
                                                        position.getAveragePrice())))
                        .sum();
        return totalCurrentValue - totalCost;
    }
}
