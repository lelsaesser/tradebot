package org.tradelite.client.finnhub.dto;

import java.util.List;

public record InsiderTransactionResponse(
        List<Transaction> data,
        String symbol
) {
    public record Transaction(
            String name,
            int share,
            int change,
            String filingDate,
            String transactionDate,
            String transactionCode,
            double transactionPrice
    ) {}
}
