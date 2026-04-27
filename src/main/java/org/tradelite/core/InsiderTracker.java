package org.tradelite.core;

import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.repository.InsiderTransactionRepository;
import org.tradelite.repository.InsiderTransactionRepository.InsiderTransactionRow;

@Component
public class InsiderTracker {

    private final FinnhubClient finnhubClient;
    private final TelegramGateway telegramClient;
    private final TargetPriceProvider targetPriceProvider;
    private final InsiderTransactionRepository insiderTransactionRepository;
    private final SymbolRegistry symbolRegistry;

    @Autowired
    public InsiderTracker(
            FinnhubClient finnhubClient,
            TelegramGateway telegramClient,
            TargetPriceProvider targetPriceProvider,
            InsiderTransactionRepository insiderTransactionRepository,
            SymbolRegistry symbolRegistry) {
        this.finnhubClient = finnhubClient;
        this.telegramClient = telegramClient;
        this.targetPriceProvider = targetPriceProvider;
        this.insiderTransactionRepository = insiderTransactionRepository;
        this.symbolRegistry = symbolRegistry;
    }

    public void trackInsiderTransactions() {
        List<String> monitoredSymbols =
                targetPriceProvider.getStockTargetPrices().stream()
                        .map(TargetPrice::getSymbol)
                        .filter(symbol -> !symbolRegistry.isEtf(symbol))
                        .toList();

        Map<StockSymbol, Map<String, Integer>> insiderTransactions = new LinkedHashMap<>();

        for (String symbolString : monitoredSymbols) {
            Optional<StockSymbol> stockSymbolOpt = symbolRegistry.fromString(symbolString);
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
                if (Objects.equals(
                        insiderTransaction.transactionCode(),
                        InsiderTransactionCodes.SELL.getCode())) {
                    insiderTransactions
                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.SELL.getCode(), 1, Integer::sum);
                }
                if (Objects.equals(
                        insiderTransaction.transactionCode(),
                        InsiderTransactionCodes.SELL_VOLUNTARY_REPORT.getCode())) {
                    insiderTransactions
                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.SELL.getCode(), 1, Integer::sum);
                }
                if (Objects.equals(
                        insiderTransaction.transactionCode(),
                        InsiderTransactionCodes.BUY.getCode())) {
                    insiderTransactions
                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.BUY.getCode(), 1, Integer::sum);
                }
                if (Objects.equals(
                        insiderTransaction.transactionCode(),
                        InsiderTransactionCodes.BUY_VOLUNTARY_REPORT.getCode())) {
                    insiderTransactions
                            .computeIfAbsent(stockSymbol, _ -> new HashMap<>())
                            .merge(InsiderTransactionCodes.BUY.getCode(), 1, Integer::sum);
                }
            }
        }

        if (!insiderTransactions.isEmpty()) {
            sendInsiderTransactionReport(insiderTransactions);
        }

        List<InsiderTransactionRow> rows = new ArrayList<>();
        for (Map.Entry<StockSymbol, Map<String, Integer>> entry : insiderTransactions.entrySet()) {
            String symbol = entry.getKey().getTicker();
            for (Map.Entry<String, Integer> txEntry : entry.getValue().entrySet()) {
                rows.add(new InsiderTransactionRow(symbol, txEntry.getKey(), txEntry.getValue()));
            }
        }
        insiderTransactionRepository.saveAll(rows);
    }

    protected void sendInsiderTransactionReport(
            Map<StockSymbol, Map<String, Integer>> insiderTransactions) {

        Map<StockSymbol, Map<String, Integer>> insiderTransactionsWithHistoricData =
                enrichWithHistoricData(insiderTransactions);
        Map<StockSymbol, Map<String, Integer>> sortedInsiderSells =
                orderMapByCodeCount(
                        insiderTransactionsWithHistoricData, InsiderTransactionCodes.SELL);
        Map<StockSymbol, Map<String, Integer>> sortedInsiderBuys =
                orderMapByCodeCount(
                        insiderTransactionsWithHistoricData, InsiderTransactionCodes.BUY);

        StringBuilder report = new StringBuilder("*Weekly Insider Transactions Report:*\n\n");

        report.append("```").append("%n".formatted());
        report.append(String.format("%-12s %-12s %-12s%n", "Symbol", "Sells", "Diff"));
        for (Map.Entry<StockSymbol, Map<String, Integer>> entry : sortedInsiderSells.entrySet()) {
            StockSymbol symbol = entry.getKey();
            Map<String, Integer> transactionTypes = entry.getValue();
            int sellCount =
                    transactionTypes.get(InsiderTransactionCodes.SELL.getCode())
                            + transactionTypes.getOrDefault(
                                    InsiderTransactionCodes.SELL_VOLUNTARY_REPORT.getCode(), 0);
            int historicSellCount =
                    transactionTypes.getOrDefault(
                            InsiderTransactionCodes.SELL_HISTORIC.getCode(), 0);

            addHistoryTransactionCountDiff(sellCount, historicSellCount, symbol, report);
        }
        report.append("```");

        report.append("%n".formatted())
                .append("%n".formatted())
                .append(("```"))
                .append("%n".formatted());
        report.append(String.format("%-12s %-12s %-12s%n", "Symbol", "Buys", "Diff"));
        for (Map.Entry<StockSymbol, Map<String, Integer>> entry : sortedInsiderBuys.entrySet()) {
            StockSymbol symbol = entry.getKey();
            Map<String, Integer> transactionTypes = entry.getValue();
            int buyCount =
                    transactionTypes.get(InsiderTransactionCodes.BUY.getCode())
                            + transactionTypes.getOrDefault(
                                    InsiderTransactionCodes.BUY_VOLUNTARY_REPORT.getCode(), 0);
            int historicBuyCount =
                    transactionTypes.getOrDefault(
                            InsiderTransactionCodes.BUY_HISTORIC.getCode(), 0);

            addHistoryTransactionCountDiff(buyCount, historicBuyCount, symbol, report);
        }
        report.append("```");

        telegramClient.sendMessage(report.toString());
    }

    protected Map<StockSymbol, Map<String, Integer>> enrichWithHistoricData(
            Map<StockSymbol, Map<String, Integer>> insiderTransactions) {
        List<InsiderTransactionRow> historicRows = insiderTransactionRepository.findAll();

        // Group rows by symbol, resolve to StockSymbol, build historic data
        Map<String, Map<String, Integer>> historicBySymbol = new HashMap<>();
        for (InsiderTransactionRow row : historicRows) {
            historicBySymbol
                    .computeIfAbsent(row.symbol(), _ -> new HashMap<>())
                    .put(row.transactionType(), row.count());
        }

        for (Map.Entry<String, Map<String, Integer>> entry : historicBySymbol.entrySet()) {
            Optional<StockSymbol> symbolOpt = symbolRegistry.fromString(entry.getKey());
            if (symbolOpt.isEmpty() || !insiderTransactions.containsKey(symbolOpt.get())) {
                continue;
            }

            StockSymbol symbol = symbolOpt.get();
            Map<String, Integer> historic = entry.getValue();

            Integer historicSellCount =
                    historic.getOrDefault(InsiderTransactionCodes.SELL.getCode(), 0);
            Integer historicBuyCount =
                    historic.getOrDefault(InsiderTransactionCodes.BUY.getCode(), 0);

            insiderTransactions
                    .get(symbol)
                    .put(InsiderTransactionCodes.SELL_HISTORIC.getCode(), historicSellCount);
            insiderTransactions
                    .get(symbol)
                    .put(InsiderTransactionCodes.BUY_HISTORIC.getCode(), historicBuyCount);
        }

        return insiderTransactions;
    }

    private Map<StockSymbol, Map<String, Integer>> orderMapByCodeCount(
            Map<StockSymbol, Map<String, Integer>> insiderTransactions,
            InsiderTransactionCodes code) {
        return insiderTransactions.entrySet().stream()
                .sorted(
                        (e1, e2) ->
                                Integer.compare(
                                        e2.getValue().getOrDefault(code.getCode(), 0),
                                        e1.getValue().getOrDefault(code.getCode(), 0)))
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue,
                                (a, _) -> a,
                                LinkedHashMap::new));
    }

    private void addHistoryTransactionCountDiff(
            int currentCount, int historicCount, StockSymbol symbol, StringBuilder report) {
        if (currentCount == 0 && historicCount == 0) {
            return;
        }
        String sign = "";
        int difference = currentCount - historicCount;
        if (difference > 0) {
            sign = "+";
        }
        String diff = sign + difference;
        report.append(String.format("%-12s %-12d %-12s%n", symbol.getTicker(), currentCount, diff));
    }
}
