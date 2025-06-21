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

    private final FinnhubClient finnhubClient;
    private final TelegramClient telegramClient;
    private final TargetPriceProvider targetPriceProvider;
    private final InsiderPersistence insiderPersistence;

    @Autowired
    public InsiderTracker(FinnhubClient finnhubClient, TelegramClient telegramClient, TargetPriceProvider targetPriceProvider,
                          InsiderPersistence insiderPersistence) {
        this.finnhubClient = finnhubClient;
        this.telegramClient = telegramClient;
        this.targetPriceProvider = targetPriceProvider;
        this.insiderPersistence = insiderPersistence;
    }

    public void trackInsiderTransactions() {
        List<String> monitoredSymbols = targetPriceProvider.getStockTargetPrices().stream().map(TargetPrice::getSymbol).toList();

        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new LinkedHashMap<>();

        for (String symbolString : monitoredSymbols) {
            StockSymbol stockSymbol = StockSymbol.fromString(symbolString).orElseThrow();

            InsiderTransactionResponse response = finnhubClient.getInsiderTransactions(stockSymbol);

            Map<String, Integer> sells = new HashMap<>();
            sells.put(InsiderTransactionCodes.SELL.getCode(), 0);
            insiderTransactions.put(stockSymbol, sells);

            for (InsiderTransactionResponse.Transaction insiderTransaction : response.data()) {
                if (Objects.equals(insiderTransaction.transactionCode(), InsiderTransactionCodes.SELL.getCode())) {
                    insiderTransactions.computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.SELL.getCode(), 1, Integer::sum);

                }
            }
        }

        sendInsiderTransactionReport(insiderTransactions);

        insiderPersistence.persistToFile(insiderTransactions, InsiderPersistence.PERSISTENCE_FILE_PATH);
    }

    protected void sendInsiderTransactionReport(Map<StockSymbol, Map<String, Integer>> insiderTransactions) {

        Map<StockSymbol, Map<String, Integer>> insiderTransactionsWithHistoricData = enrichWithHistoricData(insiderTransactions);

        StringBuilder report = new StringBuilder("*Weekly Insider Transactions Report:*\n\n");

        report.append("```").append("%n".formatted());
        report.append(String.format("%-12s %-12s %-12s%n", "Symbol", "Sells", "Diff"));


        for (Map.Entry<StockSymbol, Map<String, Integer>> entry : insiderTransactionsWithHistoricData.entrySet()) {
            StockSymbol symbol = entry.getKey();
            Map<String, Integer> transactionTypes = entry.getValue();
            int sellCount = transactionTypes.get(InsiderTransactionCodes.SELL.getCode());
            int historicSellCount = transactionTypes.getOrDefault(InsiderTransactionCodes.SELL_HISTORIC.getCode(), 0);

            if (sellCount > 0 || historicSellCount > 0) {

                String sign = "";
                int sellDifference = sellCount - historicSellCount;
                if (sellDifference > 0) {
                    sign = "+";
                }
                String diff = sign + sellDifference;
                report.append(String.format("%-12s %-12d %-12s%n",
                        symbol.getTicker(), sellCount, diff));
            }
        }
        report.append("```");

        telegramClient.sendMessage(report.toString());
    }

    private Map<StockSymbol, Map<String, Integer>> enrichWithHistoricData(Map<StockSymbol, Map<String, Integer>> insiderTransactions) {
        List<InsiderTransactionHistoric> historicData = insiderPersistence.readFromFile(InsiderPersistence.PERSISTENCE_FILE_PATH);

        for (InsiderTransactionHistoric historic : historicData) {
            StockSymbol symbol = historic.getSymbol();

            insiderTransactions.get(symbol).put(InsiderTransactionCodes.SELL_HISTORIC.getCode(), historic.getTransactions().get(InsiderTransactionCodes.SELL.getCode()));
        }

        return insiderTransactions;

    }

}
