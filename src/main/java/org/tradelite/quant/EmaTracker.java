package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.StockSymbolRegistry;

/**
 * Tracks EMA (Exponential Moving Average) positions across all tracked stocks and sends a daily
 * Telegram report classifying each stock as GREEN, YELLOW, or RED based on how many of the 5 EMAs
 * (9, 21, 50, 100, 200 day) its current price is below.
 *
 * <ul>
 *   <li><b>GREEN</b> 🟢: Below 0 or 1 EMAs — healthy uptrend
 *   <li><b>YELLOW</b> 🟡: Below 2-4 EMAs — weakening trend
 *   <li><b>RED</b> 🔴: Below all 5 EMAs — bearish
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmaTracker {

    private final EmaService emaService;
    private final TelegramGateway telegramClient;
    private final StockSymbolRegistry stockSymbolRegistry;

    /** Analyzes all tracked stocks and sends a daily EMA report via Telegram. */
    public void sendDailyReport() {
        List<EmaAnalysis> analyses = analyzeAllStocks();

        if (analyses.isEmpty()) {
            log.warn("No EMA data available — insufficient price history for all symbols");
            return;
        }

        String report = buildReport(analyses);
        telegramClient.sendMessage(report);
        log.info("Daily EMA report sent: {} symbols analyzed", analyses.size());
    }

    /** Analyzes all tracked stocks and returns EMA analysis results. */
    List<EmaAnalysis> analyzeAllStocks() {
        List<EmaAnalysis> results = new ArrayList<>();

        for (StockSymbol stock : stockSymbolRegistry.getAll()) {
            Optional<EmaAnalysis> analysis =
                    emaService.analyze(stock.getTicker(), stock.getCompanyName());
            analysis.ifPresent(results::add);
        }

        return results;
    }

    /** Builds the Telegram report message from EMA analysis results. */
    String buildReport(List<EmaAnalysis> analyses) {
        List<EmaAnalysis> green =
                analyses.stream().filter(a -> a.signalType() == EmaSignalType.GREEN).toList();
        List<EmaAnalysis> yellow =
                analyses.stream().filter(a -> a.signalType() == EmaSignalType.YELLOW).toList();
        List<EmaAnalysis> red =
                analyses.stream().filter(a -> a.signalType() == EmaSignalType.RED).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("*EMA Daily Report*\n");
        sb.append(
                String.format(
                        "_%d symbols: %d 🟢 | %d 🟡 | %d 🔴_%n%n",
                        analyses.size(), green.size(), yellow.size(), red.size()));

        if (!green.isEmpty()) {
            sb.append("🟢 *Above EMAs*\n");
            for (EmaAnalysis a : green) {
                sb.append(formatLine(a)).append("\n");
            }
            sb.append("\n");
        }

        if (!yellow.isEmpty()) {
            sb.append("🟡 *Mixed*\n");
            for (EmaAnalysis a : yellow) {
                sb.append(formatLine(a)).append("\n");
            }
            sb.append("\n");
        }

        if (!red.isEmpty()) {
            sb.append("🔴 *Below All EMAs*\n");
            for (EmaAnalysis a : red) {
                sb.append(formatLine(a)).append("\n");
            }
            sb.append("\n");
        }

        sb.append("_EMAs: 9, 21, 50, 100, 200 day_");

        return sb.toString();
    }

    private String formatLine(EmaAnalysis a) {
        return String.format(
                "%s `%s` $%.2f (%d/5 below)",
                a.signalType().getEmoji(), a.symbol(), a.currentPrice(), a.emasBelow());
    }
}
