package org.tradelite.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.*;

@Component
public class InsiderTracker {

    private static final String INSIDER_SELLS = "sells";

    private final FinnhubClient finnhubClient;
    private final TelegramClient telegramClient;
    private final TargetPriceProvider targetPriceProvider;

    @Autowired
    public InsiderTracker(FinnhubClient finnhubClient, TelegramClient telegramClient, TargetPriceProvider targetPriceProvider) {
        this.finnhubClient = finnhubClient;
        this.telegramClient = telegramClient;
        this.targetPriceProvider = targetPriceProvider;
    }

    public void trackInsiderTransactions() {
        List<String> monitoredSymbols = targetPriceProvider.getStockTargetPrices().stream().map(TargetPrice::getSymbol).toList();

        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new EnumMap<>(StockSymbol.class);

        for (String symbolString : monitoredSymbols) {
            StockSymbol stockSymbol = StockSymbol.fromString(symbolString).orElseThrow();

            InsiderTransactionResponse response = finnhubClient.getInsiderTransactions(stockSymbol);

            Map<String, Integer> sells = new HashMap<>();
            sells.put(INSIDER_SELLS, 0);
            insiderTransactions.put(stockSymbol, sells);

            for (InsiderTransactionResponse.Transaction insiderTransaction : response.data()) {
                if (Objects.equals(insiderTransaction.transactionCode(), "S")) {
                    insiderTransactions.computeIfAbsent(stockSymbol, k -> new HashMap<>())
                            .merge(INSIDER_SELLS, 1, Integer::sum);

                }
            }
        }

        sendInsiderTransactionReport(insiderTransactions);
    }

    private void sendInsiderTransactionReport(Map<StockSymbol, Map<String, Integer>> insiderTransactions) {
        StringBuilder report = new StringBuilder("*Weekly Insider Transactions Report:*\n\n");

        report.append("```\n");

        for (Map.Entry<StockSymbol, Map<String, Integer>> entry : insiderTransactions.entrySet()) {
            StockSymbol symbol = entry.getKey();
            Map<String, Integer> transactionTypes = entry.getValue();
            int sellCount = transactionTypes.get(INSIDER_SELLS);

            if (sellCount > 0) {
                report.append(String.format("%s: %d sells%n", symbol.getTicker(), sellCount));
            }
        }
        report.append("```");

        telegramClient.sendMessage(report.toString());
    }

}
