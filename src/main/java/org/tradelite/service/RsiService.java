package org.tradelite.service;

import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TickerSymbol;
import org.tradelite.service.model.DailyPrice;

@Slf4j
@Service
@RequiredArgsConstructor
public class RsiService {

    static final int RSI_PERIOD = 14;
    static final int RSI_LOOKBACK_DAYS = 30;

    private final TelegramGateway telegramClient;
    private final DailyPriceProvider dailyPriceProvider;
    private final LivePriceSource livePriceSource;
    private final SymbolRegistry symbolRegistry;

    /**
     * Stores the Telegram message ID of the last sent RSI report so it can be deleted before
     * sending the next update.
     */
    private Long lastTelegramReportMessageId;

    /** Tracks the previous RSI value per symbol for delta display in reports. */
    private final Map<String, Double> previousRsiMap = new HashMap<>();

    /**
     * Analyzes all symbols from SymbolRegistry, calculates RSI, detects overbought/oversold
     * signals, builds a consolidated report, and sends it via Telegram. Deletes the previous report
     * message.
     */
    public void sendRsiReport() {
        List<RsiSignal> signals = analyzeAllSymbols();

        if (signals.isEmpty()) {
            log.info("No RSI signals to report");
            return;
        }

        deletePreviousTelegramReport();
        String report = buildRsiReport(signals);
        OptionalLong messageId = telegramClient.sendMessageAndReturnId(report);
        messageId.ifPresent(id -> lastTelegramReportMessageId = id);
        log.info("RSI report sent with {} signal(s)", signals.size());
    }

    /**
     * Iterates over all symbols from SymbolRegistry, calculates RSI for each, and returns signals
     * for those in overbought or oversold territory.
     */
    protected List<RsiSignal> analyzeAllSymbols() {
        List<RsiSignal> signals = new ArrayList<>();

        for (StockSymbol stockSymbol : symbolRegistry.getAll()) {
            String symbolKey = stockSymbol.getName();
            String displayName = stockSymbol.getDisplayName();

            List<Double> prices = fetchPrices(symbolKey);
            livePriceSource
                    .getPriceByKey(symbolKey)
                    .ifPresent(
                            currentPrice -> {
                                prices.add(currentPrice);
                                log.info(
                                        "Using current price {} from cache for RSI report of {}",
                                        currentPrice,
                                        symbolKey);
                            });

            if (prices.size() < RSI_PERIOD + 1) {
                continue;
            }

            double rsi = calculateRsi(prices);
            double previousRsi = previousRsiMap.getOrDefault(symbolKey, 0.0);
            double rsiDiff = rsi - previousRsi;

            if (rsi >= 70) {
                log.info("RSI for {} is in overbought zone: {}", displayName, rsi);
                signals.add(
                        new RsiSignal(
                                displayName, rsi, previousRsi, rsiDiff, RsiSignal.Zone.OVERBOUGHT));
            } else if (rsi <= 30) {
                log.info("RSI for {} is in oversold zone: {}", displayName, rsi);
                signals.add(
                        new RsiSignal(
                                displayName, rsi, previousRsi, rsiDiff, RsiSignal.Zone.OVERSOLD));
            }
            previousRsiMap.put(symbolKey, rsi);
        }

        return signals;
    }

    public Optional<Double> getCurrentRsi(TickerSymbol symbol) {
        String symbolKey = symbol.getName();

        List<Double> historicalPrices = fetchPrices(symbolKey);
        livePriceSource
                .getPrice(symbol)
                .ifPresent(
                        currentPrice -> {
                            historicalPrices.add(currentPrice);
                            log.info(
                                    "Using current price {} from cache for RSI calculation of {}",
                                    currentPrice,
                                    symbol.getName());
                        });

        if (historicalPrices.size() < RSI_PERIOD + 1) {
            return Optional.empty();
        }

        return Optional.of(calculateRsi(historicalPrices));
    }

    private List<Double> fetchPrices(String symbolKey) {
        List<DailyPrice> dailyPrices =
                dailyPriceProvider.findDailyClosingPrices(symbolKey, RSI_LOOKBACK_DAYS);
        return new ArrayList<>(dailyPrices.stream().map(DailyPrice::getPrice).toList());
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

    /** Builds a consolidated RSI report message from the given signals. */
    protected String buildRsiReport(List<RsiSignal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("*RSI Signal Report*\n\n");

        List<RsiSignal> overbought =
                signals.stream().filter(s -> s.zone() == RsiSignal.Zone.OVERBOUGHT).toList();
        List<RsiSignal> oversold =
                signals.stream().filter(s -> s.zone() == RsiSignal.Zone.OVERSOLD).toList();

        if (!overbought.isEmpty()) {
            sb.append("🔴 *Overbought (RSI ≥ 70):*\n");
            for (RsiSignal signal : overbought) {
                sb.append(formatSignalLine(signal)).append("\n");
            }
            sb.append("\n");
        }

        if (!oversold.isEmpty()) {
            sb.append("🟢 *Oversold (RSI ≤ 30):*\n");
            for (RsiSignal signal : oversold) {
                sb.append(formatSignalLine(signal)).append("\n");
            }
            sb.append("\n");
        }

        sb.append(
                String.format(
                        "_%d signal(s): %d overbought, %d oversold_",
                        signals.size(), overbought.size(), oversold.size()));

        return sb.toString();
    }

    private String formatSignalLine(RsiSignal signal) {
        String rsiDiffString = "";
        if (signal.previousRsi() != 0) {
            rsiDiffString = String.format(" (%+.1f)", signal.rsiDiff());
        }
        return String.format("  • %s: %.2f%s", signal.displayName(), signal.rsi(), rsiDiffString);
    }

    /** Represents an RSI signal for a single symbol. */
    public record RsiSignal(
            String displayName, double rsi, double previousRsi, double rsiDiff, Zone zone) {

        public enum Zone {
            OVERBOUGHT,
            OVERSOLD
        }
    }

    protected double calculateRsi(List<Double> prices) {
        if (prices.size() < RSI_PERIOD) {
            return 50; // Not enough data
        }

        double avgGain = 0;
        double avgLoss = 0;

        // First RSI value
        double firstChange = prices.get(1) - prices.get(0);
        if (firstChange > 0) {
            avgGain = firstChange;
        } else {
            avgLoss = -firstChange;
        }

        for (int i = 2; i < RSI_PERIOD; i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss -= change;
            }
        }

        avgGain /= RSI_PERIOD;
        avgLoss /= RSI_PERIOD;

        // Subsequent RSI values
        for (int i = RSI_PERIOD; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            double gain = change > 0 ? change : 0;
            double loss = change < 0 ? -change : 0;

            avgGain = (avgGain * (RSI_PERIOD - 1) + gain) / RSI_PERIOD;
            avgLoss = (avgLoss * (RSI_PERIOD - 1) + loss) / RSI_PERIOD;
        }

        if (avgLoss == 0) {
            return 100;
        }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }
}
