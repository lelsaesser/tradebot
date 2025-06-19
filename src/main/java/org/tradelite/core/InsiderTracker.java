package org.tradelite.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class InsiderTracker {

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

        Map<StockSymbol, Integer> insiderSells = new EnumMap<>(StockSymbol.class);

        for (String symbolString : monitoredSymbols) {
            StockSymbol stockSymbol = StockSymbol.fromString(symbolString).orElseThrow();

            InsiderTransactionResponse response = finnhubClient.getInsiderTransactions(stockSymbol);

            insiderSells.put(stockSymbol, 0);

            for (InsiderTransactionResponse.Transaction insiderTransaction : response.data()) {
                if (Objects.equals(insiderTransaction.transactionCode(), "S")) {
                    insiderSells.put(stockSymbol, insiderSells.get(stockSymbol) + 1);
                }
            }
        }

        sendInsiderTransactionReport(insiderSells);
    }

    private void sendInsiderTransactionReport(Map<StockSymbol, Integer> insiderSells) {
        StringBuilder report = new StringBuilder("*Weekly Insider Transactions Report:*\n\n");

        report.append("```\n");
        for (Map.Entry<StockSymbol, Integer> entry : insiderSells.entrySet()) {
            StockSymbol symbol = entry.getKey();
            int sellCount = entry.getValue();

            if (sellCount > 0) {
                report.append(String.format("%s: %d sells%n", symbol.getTicker(), sellCount));
            }
        }
        report.append("```");

        telegramClient.sendMessage(report.toString());
    }

}
