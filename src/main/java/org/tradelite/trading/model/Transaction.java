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
public class Transaction {

    private final String symbol;
    private final TransactionType type;
    private final double quantity;
    private final double price;
    private final double totalAmount;
    private final LocalDateTime timestamp;
    private final String reason;

    @JsonCreator
    public Transaction(
            @JsonProperty("symbol") String symbol,
            @JsonProperty("type") TransactionType type,
            @JsonProperty("quantity") double quantity,
            @JsonProperty("price") double price,
            @JsonProperty("totalAmount") double totalAmount,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("reason") String reason) {
        this.symbol = symbol;
        this.type = type;
        this.quantity = quantity;
        this.price = price;
        this.totalAmount = totalAmount;
        this.timestamp = timestamp;
        this.reason = reason;
    }

    public static Transaction createBuy(
            String symbol, double quantity, double price, String reason) {
        return new Transaction(
                symbol,
                TransactionType.BUY,
                quantity,
                price,
                quantity * price,
                LocalDateTime.now(),
                reason);
    }

    public static Transaction createSell(
            String symbol, double quantity, double price, String reason) {
        return new Transaction(
                symbol,
                TransactionType.SELL,
                quantity,
                price,
                quantity * price,
                LocalDateTime.now(),
                reason);
    }

    public enum TransactionType {
        BUY,
        SELL
    }
}
