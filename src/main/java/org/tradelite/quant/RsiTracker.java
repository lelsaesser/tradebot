package org.tradelite.quant;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.tradelite.service.RsiService;
import org.tradelite.service.RsiService.RsiSignal;

@Slf4j
@Component
@RequiredArgsConstructor
public class RsiTracker {

    private final RsiService rsiService;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;

    /**
     * Stores the Telegram message ID of the last sent RSI report so it can be deleted before
     * sending the next update.
     */
    private Long lastTelegramReportMessageId;

    /** Tracks the previous RSI value per symbol for delta display in reports. */
    private final Map<String, Double> previousRsiMap = new HashMap<>();

    /**
     * Analyzes all symbols from SymbolRegistry, calculates RSI for each, detects
     * overbought/oversold signals, builds a consolidated report, and sends it via Telegram. Deletes
     * the previous report message.
     */
    public void analyzeAndSendReport() {
        List<SignalWithDelta> signals = analyzeAllSymbols();

        if (signals.isEmpty()) {
            log.info("No RSI signals to report");
            return;
        }

        deletePreviousTelegramReport();
        String report = buildReport(signals);
        OptionalLong messageId = telegramClient.sendMessageAndReturnId(report);
        messageId.ifPresent(id -> lastTelegramReportMessageId = id);
        log.info("RSI report sent with {} signal(s)", signals.size());
    }

    private List<SignalWithDelta> analyzeAllSymbols() {
        List<SignalWithDelta> signals = new ArrayList<>();

        for (StockSymbol stockSymbol : symbolRegistry.getAll()) {
            String symbolKey = stockSymbol.getName();
            String displayName = stockSymbol.getDisplayName();

            Optional<RsiSignal> signal = rsiService.analyze(symbolKey, displayName);
            signal.ifPresent(
                    s -> {
                        double previousRsi = previousRsiMap.getOrDefault(symbolKey, 0.0);
                        double rsiDiff = s.rsi() - previousRsi;
                        signals.add(new SignalWithDelta(s, previousRsi, rsiDiff));
                    });
            // Update previousRsi for all analyzed symbols (not just those with signals)
            rsiService
                    .getCurrentRsi(stockSymbol)
                    .ifPresent(rsi -> previousRsiMap.put(symbolKey, rsi));
        }

        return signals;
    }

    private void deletePreviousTelegramReport() {
        if (lastTelegramReportMessageId != null) {
            log.info(
                    "Deleting previous Telegram RSI report message {}",
                    lastTelegramReportMessageId);
            telegramClient.deleteMessage(lastTelegramReportMessageId);
            lastTelegramReportMessageId = null;
        }
    }

    private String buildReport(List<SignalWithDelta> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("*RSI Signal Report*\n\n");

        List<SignalWithDelta> overbought =
                signals.stream()
                        .filter(s -> s.signal().zone() == RsiSignal.Zone.OVERBOUGHT)
                        .toList();
        List<SignalWithDelta> oversold =
                signals.stream().filter(s -> s.signal().zone() == RsiSignal.Zone.OVERSOLD).toList();

        if (!overbought.isEmpty()) {
            sb.append("🔴 *Overbought (RSI ≥ 70):*\n");
            for (SignalWithDelta s : overbought) {
                sb.append(formatSignalLine(s)).append("\n");
            }
            sb.append("\n");
        }

        if (!oversold.isEmpty()) {
            sb.append("🟢 *Oversold (RSI ≤ 30):*\n");
            for (SignalWithDelta s : oversold) {
                sb.append(formatSignalLine(s)).append("\n");
            }
            sb.append("\n");
        }

        sb.append(
                String.format(
                        "_%d signal(s): %d overbought, %d oversold_",
                        signals.size(), overbought.size(), oversold.size()));

        return sb.toString();
    }

    private String formatSignalLine(SignalWithDelta s) {
        String rsiDiffString = "";
        if (s.previousRsi() != 0) {
            rsiDiffString = String.format(" (%+.1f)", s.rsiDiff());
        }
        return String.format(
                "  • %s: %.2f%s", s.signal().displayName(), s.signal().rsi(), rsiDiffString);
    }

    record SignalWithDelta(RsiSignal signal, double previousRsi, double rsiDiff) {}
}
