package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.OptionalLong;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.CoinId;
import org.tradelite.common.SymbolType;
import org.tradelite.common.TickerSymbol;
import org.tradelite.core.CoinGeckoPriceEvaluator;
import org.tradelite.core.FinnhubPriceEvaluator;
import org.tradelite.service.model.RsiDailyClosePrice;

@Slf4j
@Service
public class RsiService {

    private static final int RSI_PERIOD = 14;
    private static final String RSI_DATA_FILE = "config/rsi-data.json";

    private final TelegramClient telegramClient;
    private final ObjectMapper objectMapper;

    @Getter private Map<String, RsiDailyClosePrice> priceHistory = new HashMap<>();

    private final FinnhubPriceEvaluator finnhubPriceEvaluator;
    private final CoinGeckoPriceEvaluator coinGeckoPriceEvaluator;

    /**
     * Stores the Telegram message ID of the last sent RSI report so it can be deleted before
     * sending the next update.
     */
    private Long lastTelegramReportMessageId;

    /** Maps symbol keys to their display names for use when building RSI reports. */
    @Getter private final Map<String, String> symbolDisplayNames = new HashMap<>();

    @Autowired
    public RsiService(
            TelegramClient telegramClient,
            ObjectMapper objectMapper,
            FinnhubPriceEvaluator finnhubPriceEvaluator,
            CoinGeckoPriceEvaluator coinGeckoPriceEvaluator)
            throws IOException {
        this.telegramClient = telegramClient;
        this.objectMapper = objectMapper;
        this.finnhubPriceEvaluator = finnhubPriceEvaluator;
        this.coinGeckoPriceEvaluator = coinGeckoPriceEvaluator;
        loadPriceHistory();
    }

    public void addPrice(TickerSymbol symbol, double price, LocalDate date) throws IOException {
        String symbolKey = symbol.getName();
        RsiDailyClosePrice rsiDailyClosePrice =
                priceHistory.getOrDefault(symbolKey, new RsiDailyClosePrice());

        if (finnhubPriceEvaluator.isPotentialMarketHoliday(symbolKey, price)) {
            log.info(
                    "Potential market holiday detected for {}: price {} on {} is identical to previous trading day. Skipping price update.",
                    symbol,
                    price,
                    date);
            return;
        }

        rsiDailyClosePrice.addPrice(date, price);
        priceHistory.put(symbolKey, rsiDailyClosePrice);
        savePriceHistory();

        String displayName =
                symbol.getSymbolType() == SymbolType.STOCK
                        ? symbol.getDisplayName()
                        : symbol.getName();
        symbolDisplayNames.put(symbolKey, displayName);
    }

    /**
     * Analyzes all symbols in priceHistory, calculates RSI, detects overbought/oversold signals,
     * builds a consolidated report, and sends it via Telegram. Deletes the previous report message.
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
     * Iterates over all symbols in priceHistory, calculates RSI for each, and returns signals for
     * those in overbought or oversold territory.
     */
    protected List<RsiSignal> analyzeAllSymbols() {
        List<RsiSignal> signals = new ArrayList<>();

        for (Map.Entry<String, RsiDailyClosePrice> entry : priceHistory.entrySet()) {
            String symbolKey = entry.getKey();
            RsiDailyClosePrice rsiDailyClosePrice = entry.getValue();

            if (rsiDailyClosePrice.getPrices().size() < RSI_PERIOD) {
                continue;
            }

            List<Double> prices = new ArrayList<>(rsiDailyClosePrice.getPriceValues());
            Double currentPrice = getCurrentPriceFromCacheByKey(symbolKey);
            if (currentPrice != null) {
                prices.add(currentPrice);
                log.info(
                        "Using current price {} from cache for RSI report of {}",
                        currentPrice,
                        symbolKey);
            }

            if (prices.size() < RSI_PERIOD + 1) {
                continue;
            }

            double rsi = calculateRsi(prices);
            String displayName = symbolDisplayNames.getOrDefault(symbolKey, symbolKey);
            double previousRsi = rsiDailyClosePrice.getPreviousRsi();
            double rsiDiff = rsi - previousRsi;

            if (rsi >= 70) {
                log.info("RSI for {} is in overbought zone: {}", displayName, rsi);
                signals.add(new RsiSignal(displayName, rsi, previousRsi, rsiDiff, "OVERBOUGHT"));
            } else if (rsi <= 30) {
                log.info("RSI for {} is in oversold zone: {}", displayName, rsi);
                signals.add(new RsiSignal(displayName, rsi, previousRsi, rsiDiff, "OVERSOLD"));
            }
            rsiDailyClosePrice.setPreviousRsi(rsi);
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

    /** Builds a consolidated RSI report message from the given signals. */
    protected String buildRsiReport(List<RsiSignal> signals) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *RSI Signal Report*\n\n");

        List<RsiSignal> overbought =
                signals.stream().filter(s -> "OVERBOUGHT".equals(s.zone())).toList();
        List<RsiSignal> oversold =
                signals.stream().filter(s -> "OVERSOLD".equals(s.zone())).toList();

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
            String displayName, double rsi, double previousRsi, double rsiDiff, String zone) {}

    public Optional<Double> getCurrentRsi(TickerSymbol symbol) {
        String symbolKey = symbol.getName();
        RsiDailyClosePrice rsiDailyClosePrice = priceHistory.get(symbolKey);

        if (rsiDailyClosePrice == null || rsiDailyClosePrice.getPrices().size() < RSI_PERIOD) {
            return Optional.empty();
        }

        List<Double> historicalPrices = new ArrayList<>(rsiDailyClosePrice.getPriceValues());
        Double currentPrice = getCurrentPriceFromCache(symbol);
        if (currentPrice != null) {
            // Add the current price to get the most up-to-date RSI calculation
            historicalPrices.add(currentPrice);
            log.info(
                    "Using current price {} from cache for RSI calculation of {}",
                    currentPrice,
                    symbol.getName());
        }

        if (historicalPrices.size() < RSI_PERIOD + 1) {
            return Optional.empty();
        }

        return Optional.of(calculateRsi(historicalPrices));
    }

    protected Double getCurrentPriceFromCache(TickerSymbol symbol) {
        if (symbol.getSymbolType() == SymbolType.STOCK) {
            return finnhubPriceEvaluator.getLastPriceCache().get(symbol.getName());
        } else if (symbol.getSymbolType() == SymbolType.CRYPTO) {
            CoinId coinId = (CoinId) symbol;
            return coinGeckoPriceEvaluator.getLastPriceCache().get(coinId);
        }
        return null;
    }

    /**
     * Looks up the current price from the Finnhub or CoinGecko cache using only the symbol key
     * string. Tries the stock cache first, then checks if it matches a known CoinId.
     */
    protected Double getCurrentPriceFromCacheByKey(String symbolKey) {
        Double stockPrice = finnhubPriceEvaluator.getLastPriceCache().get(symbolKey);
        if (stockPrice != null) {
            return stockPrice;
        }
        Optional<CoinId> coinId = CoinId.fromString(symbolKey);
        return coinId.map(id -> coinGeckoPriceEvaluator.getLastPriceCache().get(id)).orElse(null);
    }

    public synchronized boolean removeSymbolRsiData(String symbolKey) {
        if (symbolKey == null || symbolKey.isEmpty()) {
            return false;
        }

        boolean removed = priceHistory.remove(symbolKey) != null;
        if (removed) {
            try {
                savePriceHistory();
                log.info("Removed RSI data for symbol: {}", symbolKey);
                return true;
            } catch (IOException e) {
                log.error("Failed to save RSI data after removing symbol: {}", symbolKey, e);
                return false;
            }
        }

        return false;
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

    protected void loadPriceHistory() throws IOException {
        try {
            File file = new File(RSI_DATA_FILE);
            if (file.exists()) {
                priceHistory =
                        objectMapper.readValue(
                                file,
                                objectMapper
                                        .getTypeFactory()
                                        .constructMapType(
                                                HashMap.class,
                                                String.class,
                                                RsiDailyClosePrice.class));
            }
        } catch (IOException e) {
            log.error("Error loading RSI data", e);
            throw e;
        }
    }

    protected void savePriceHistory() throws IOException {
        try {
            objectMapper.writeValue(new File(RSI_DATA_FILE), priceHistory);
        } catch (IOException e) {
            log.error("Error saving RSI data", e);
            throw e;
        }
    }
}
