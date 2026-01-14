package org.tradelite.trading.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public class Position {

    private final String symbol;
    private final double quantity;
    private final double averagePrice;
    private final LocalDateTime firstPurchaseDate;
    private final LocalDateTime lastPurchaseDate;

    @JsonCreator
    public Position(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("quantity") double quantity,
            @JsonProperty("averagePrice") double averagePrice,
            @JsonProperty("firstPurchaseDate") LocalDateTime firstPurchaseDate,
            @JsonProperty("lastPurchaseDate") LocalDateTime lastPurchaseDate) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.averagePrice = averagePrice;
        this.firstPurchaseDate = firstPurchaseDate;
        this.lastPurchaseDate = lastPurchaseDate;
    }

    public static Position create(String symbol, double quantity, double price) {
        LocalDateTime now = LocalDateTime.now();
        return new Position(symbol, quantity, price, now, now);
    }

    public Position addQuantity(double additionalQuantity, double price) {
        double newTotalCost = (this.quantity * this.averagePrice) + (additionalQuantity * price);
        double newTotalQuantity = this.quantity + additionalQuantity;
        double newAveragePrice = newTotalCost / newTotalQuantity;

        return new Position(
                this.symbol,
                newTotalQuantity,
                newAveragePrice,
                this.firstPurchaseDate,
                LocalDateTime.now());
    }

    public double getCurrentValue(double currentPrice) {
        return quantity * currentPrice;
    }

    public double getTotalCost() {
        return quantity * averagePrice;
    }

    public double getProfitLoss(double currentPrice) {
        return getCurrentValue(currentPrice) - getTotalCost();
    }

    public double getProfitLossPercentage(double currentPrice) {
        return (getProfitLoss(currentPrice) / getTotalCost()) * 100;
    }
}
