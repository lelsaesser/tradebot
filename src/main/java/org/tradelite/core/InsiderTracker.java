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
import java.util.stream.Collectors;

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
            Optional<StockSymbol> stockSymbolOpt = StockSymbol.fromString(symbolString);
            if (stockSymbolOpt.isEmpty()) {
                continue;
            }
            StockSymbol stockSymbol = stockSymbolOpt.get();

            InsiderTransactionResponse response = finnhubClient.getInsiderTransactions(stockSymbol);

            Map<String, Integer> sells = new HashMap<>();
            Map<String, Integer> buys = new HashMap<>();
            sells.put(InsiderTransactionCodes.SELL.getCode(), 0);
            sells.put(InsiderTransactionCodes.SELL_VOLUNTARY_REPORT.getCode(), 0);
            buys.put(InsiderTransactionCodes.BUY.getCode(), 0);
            buys.put(InsiderTransactionCodes.BUY_VOLUNTARY_REPORT.getCode(), 0);
            Map<String, Integer> transactions = new HashMap<>();
            transactions.putAll(sells);
            transactions.putAll(buys);
            insiderTransactions.put(stockSymbol, transactions);

            for (InsiderTransactionResponse.Transaction insiderTransaction : response.data()) {
                if (Objects.equals(insiderTransaction.transactionCode(), InsiderTransactionCodes.SELL.getCode())) {
                    insiderTransactions.computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.SELL.getCode(), 1, Integer::sum);
                }
                if (Objects.equals(insiderTransaction.transactionCode(), InsiderTransactionCodes.SELL_VOLUNTARY_REPORT.getCode())) {
                    insiderTransactions.computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.SELL.getCode(), 1, Integer::sum);
                }
                if (Objects.equals(insiderTransaction.transactionCode(), InsiderTransactionCodes.BUY.getCode())) {
                    insiderTransactions.computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.BUY.getCode(), 1, Integer::sum);
                }
                if (Objects.equals(insiderTransaction.transactionCode(), InsiderTransactionCodes.BUY_VOLUNTARY_REPORT.getCode())) {
                    insiderTransactions.computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.BUY.getCode(), 1, Integer::sum);
                }
            }
        }

        if (!insiderTransactions.isEmpty()) {
            sendInsiderTransactionReport(insiderTransactions);
        }

        insiderPersistence.persistToFile(insiderTransactions, InsiderPersistence.PERSISTENCE_FILE_PATH);
    }

    protected void sendInsiderTransactionReport(Map<StockSymbol, Map<String, Integer>> insiderTransactions) {

        Map<StockSymbol, Map<String, Integer>> insiderTransactionsWithHistoricData = enrichWithHistoricData(insiderTransactions);
        Map<StockSymbol, Map<String, Integer>> sortedInsiderSells = orderMapByCodeCount(insiderTransactionsWithHistoricData, InsiderTransactionCodes.SELL);
        Map<StockSymbol, Map<String, Integer>> sortedInsiderBuys = orderMapByCodeCount(insiderTransactionsWithHistoricData, InsiderTransactionCodes.BUY);

        StringBuilder report = new StringBuilder("*Weekly Insider Transactions Report:*\n\n");

        report.append("```").append("%n".formatted());
        report.append(String.format("%-12s %-12s %-12s%n", "Symbol", "Sells", "Diff"));
        for (Map.Entry<StockSymbol, Map<String, Integer>> entry : sortedInsiderSells.entrySet()) {
            StockSymbol symbol = entry.getKey();
            Map<String, Integer> transactionTypes = entry.getValue();
            int sellCount = transactionTypes.get(InsiderTransactionCodes.SELL.getCode()) + transactionTypes.getOrDefault(InsiderTransactionCodes.SELL_VOLUNTARY_REPORT.getCode(), 0);
            int historicSellCount = transactionTypes.getOrDefault(InsiderTransactionCodes.SELL_HISTORIC.getCode(), 0);

            addHistoryTransactionCountDiff(sellCount, historicSellCount, symbol, report);
        }
        report.append("```");

        report.append("%n".formatted()).append("%n".formatted()).append(("```")).append("%n".formatted());
        report.append(String.format("%-12s %-12s %-12s%n", "Symbol", "Buys", "Diff"));
        for (Map.Entry<StockSymbol, Map<String, Integer>> entry : sortedInsiderBuys.entrySet()) {
            StockSymbol symbol = entry.getKey();
            Map<String, Integer> transactionTypes = entry.getValue();
            int buyCount = transactionTypes.get(InsiderTransactionCodes.BUY.getCode()) + transactionTypes.getOrDefault(InsiderTransactionCodes.BUY_VOLUNTARY_REPORT.getCode(), 0);
            int historicBuyCount = transactionTypes.getOrDefault(InsiderTransactionCodes.BUY_HISTORIC.getCode(), 0);

            addHistoryTransactionCountDiff(buyCount, historicBuyCount, symbol, report);
        }
        report.append("```");

        telegramClient.sendMessage(report.toString());
    }

    protected Map<StockSymbol, Map<String, Integer>> enrichWithHistoricData(Map<StockSymbol, Map<String, Integer>> insiderTransactions) {
        List<InsiderTransactionHistoric> historicData = insiderPersistence.readFromFile(InsiderPersistence.PERSISTENCE_FILE_PATH);

        for (InsiderTransactionHistoric historic : historicData) {
            StockSymbol symbol = historic.getSymbol();

            Integer historicSellTransactionCount = historic.getTransactions().get(InsiderTransactionCodes.SELL.getCode());
            Integer historicBuyTransactionCount = historic.getTransactions().get(InsiderTransactionCodes.BUY.getCode());
            if (historicBuyTransactionCount == null) {
                historicBuyTransactionCount = 0;
            }
            if (historicSellTransactionCount == null) {
                historicSellTransactionCount = 0;
            }

            insiderTransactions.get(symbol).put(InsiderTransactionCodes.SELL_HISTORIC.getCode(), historicSellTransactionCount);
            insiderTransactions.get(symbol).put(InsiderTransactionCodes.BUY_HISTORIC.getCode(), historicBuyTransactionCount);
        }

        return insiderTransactions;
    }

    private Map<StockSymbol, Map<String, Integer>> orderMapByCodeCount(Map<StockSymbol, Map<String, Integer>> insiderTransactions, InsiderTransactionCodes code) {
        return insiderTransactions.entrySet().stream()
                .sorted((e1, e2) -> Integer.compare(
                        e2.getValue().getOrDefault(code.getCode(), 0),
                        e1.getValue().getOrDefault(code.getCode(), 0)))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, _) -> a,
                        LinkedHashMap::new
                ));
    }

    private void addHistoryTransactionCountDiff(int currentCount, int historicCount, StockSymbol symbol, StringBuilder report) {
        String sign = "";
        int difference = currentCount - historicCount;
        if (difference > 0) {
            sign = "+";
        }
        String diff = sign + difference;
        report.append(String.format("%-12s %-12d %-12s%n",
                symbol.getTicker(), currentCount, diff));
    }
}
