package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;

/**
 * Tracks Bollinger Band signals across sector ETFs, SPY, and all tracked stocks to detect
 * volatility extremes.
 *
 * <p>Monitors three signal types and sends Telegram alerts when actionable conditions arise:
 *
 * <ul>
 *   <li><b>Upper Band Touch</b>: Price above upper band — may be overextended
 *   <li><b>Lower Band Touch</b>: Price below lower band — may be underextended
 *   <li><b>Squeeze</b>: Bandwidth at historically low levels — breakout expected
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BollingerBandTracker {

    private final BollingerBandService bollingerBandService;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;

    /**
     * Stores the Telegram message ID of the last sent hourly alert so it can be deleted before
     * sending the next update.
     */
    private Long lastTelegramReportMessageId;

    /** Analyzes Bollinger Bands for all tracked sector ETFs and returns the results. */
    public List<BollingerBandAnalysis> analyzeAllSectors() {
        List<BollingerBandAnalysis> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : symbolRegistry.getAllEtfs().entrySet()) {
            String symbol = entry.getKey();
            String displayName = entry.getValue();

            Optional<BollingerBandAnalysis> analysis =
                    bollingerBandService.analyze(symbol, displayName);
            analysis.ifPresent(results::add);
        }

        return results;
    }

    /** Analyzes Bollinger Bands for all tracked stocks (excluding ETFs to avoid duplication). */
    public List<BollingerBandAnalysis> analyzeAllStocks() {
        List<BollingerBandAnalysis> results = new ArrayList<>();

        for (StockSymbol stock : symbolRegistry.getStocks()) {
            Optional<BollingerBandAnalysis> analysis =
                    bollingerBandService.analyze(stock.getTicker(), stock.getCompanyName());
            analysis.ifPresent(results::add);
        }

        return results;
    }

    /** Performs hourly Bollinger Band check and sends Telegram alerts for actionable signals. */
    public void analyzeAndSendAlerts() {
        List<BollingerBandAnalysis> sectorAnalyses = analyzeAllSectors();
        List<BollingerBandAnalysis> stockAnalyses = analyzeAllStocks();

        List<BollingerBandAnalysis> allAnalyses = new ArrayList<>(sectorAnalyses);
        allAnalyses.addAll(stockAnalyses);

        if (allAnalyses.isEmpty()) {
            log.warn("No Bollinger Band data available — insufficient price history");
            return;
        }

        List<BollingerBandAnalysis> withSignals =
                allAnalyses.stream().filter(BollingerBandAnalysis::hasSignals).toList();

        // Delete previous hourly report before sending new one
        deletePreviousTelegramReport();

        if (!withSignals.isEmpty()) {
            String alertMessage = buildAlertMessage(allAnalyses, withSignals);
            OptionalLong messageId = telegramClient.sendMessageAndReturnId(alertMessage);
            messageId.ifPresent(id -> lastTelegramReportMessageId = id);
            log.info(
                    "Bollinger Band alert sent: {} symbol(s) with signals out of {} analyzed",
                    withSignals.size(),
                    allAnalyses.size());
        } else {
            log.info(
                    "Bollinger Band check complete: all {} symbols within normal range",
                    allAnalyses.size());
        }
    }

    private void deletePreviousTelegramReport() {
        if (lastTelegramReportMessageId != null) {
            log.info(
                    "Deleting previous Telegram BB report message {}", lastTelegramReportMessageId);
            telegramClient.deleteMessage(lastTelegramReportMessageId);
            lastTelegramReportMessageId = null;
        }
    }

    private String buildAlertMessage(
            List<BollingerBandAnalysis> allAnalyses, List<BollingerBandAnalysis> withSignals) {

        StringBuilder sb = new StringBuilder();

        // Header with severity indicator
        boolean hasSqueeze =
                withSignals.stream().anyMatch(a -> a.isSqueeze() || a.isHistoricalSqueeze());
        boolean hasOverextended =
                withSignals.stream().anyMatch(BollingerBandAnalysis::isOverextended);
        boolean hasUnderextended =
                withSignals.stream().anyMatch(BollingerBandAnalysis::isUnderextended);

        if (hasSqueeze) {
            sb.append("🔵 *Bollinger Band Alert — Squeeze Detected*\n\n");
        } else if (hasOverextended || hasUnderextended) {
            sb.append("🟡 *Bollinger Band Alert — Band Touch*\n\n");
        } else {
            sb.append("*Bollinger Band Daily Report*\n\n");
        }

        // Group by signal type
        List<BollingerBandAnalysis> squeezes =
                withSignals.stream().filter(a -> a.isSqueeze() || a.isHistoricalSqueeze()).toList();

        List<BollingerBandAnalysis> overextended =
                withSignals.stream().filter(BollingerBandAnalysis::isOverextended).toList();

        List<BollingerBandAnalysis> underextended =
                withSignals.stream().filter(BollingerBandAnalysis::isUnderextended).toList();

        if (!squeezes.isEmpty()) {
            sb.append("*Volatility Squeeze* (breakout expected):\n");
            for (BollingerBandAnalysis analysis : squeezes) {
                sb.append(analysis.toSummaryLine()).append("\n");
            }
            sb.append("\n");
        }

        if (!overextended.isEmpty()) {
            sb.append("*Upper Band Touch* (overextended):\n");
            for (BollingerBandAnalysis analysis : overextended) {
                sb.append(analysis.toSummaryLine()).append("\n");
            }
            sb.append("\n");
        }

        if (!underextended.isEmpty()) {
            sb.append("*Lower Band Touch* (underextended):\n");
            for (BollingerBandAnalysis analysis : underextended) {
                sb.append(analysis.toSummaryLine()).append("\n");
            }
            sb.append("\n");
        }

        // Compact summary of all symbols
        sb.append("All symbols: ");
        for (BollingerBandAnalysis analysis : allAnalyses) {
            sb.append(analysis.toCompactLine()).append(" ");
        }
        sb.append("\n\n");

        return sb.toString();
    }
}
