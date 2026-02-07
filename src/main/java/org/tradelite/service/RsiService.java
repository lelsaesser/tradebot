package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.CoinId;
import org.tradelite.common.StockSymbol;
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

        if (isPotentialMarketHoliday(rsiDailyClosePrice, price, date)) {
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
        calculateAndNotifyRsi(symbol, rsiDailyClosePrice);
    }

    private boolean isPotentialMarketHoliday(
            RsiDailyClosePrice rsiDailyClosePrice, double newPrice, LocalDate newDate) {
        if (rsiDailyClosePrice.getPrices().isEmpty()) {
            return false;
        }

        var prices = rsiDailyClosePrice.getPrices();
        prices.sort((p1, p2) -> p2.getDate().compareTo(p1.getDate()));
        var mostRecentPrice = prices.getFirst();

        double epsilon = 0.0001;
        boolean pricesAreIdentical = Math.abs(newPrice - mostRecentPrice.getPrice()) < epsilon;

        return pricesAreIdentical && !newDate.isEqual(mostRecentPrice.getDate());
    }

    private void calculateAndNotifyRsi(TickerSymbol symbol, RsiDailyClosePrice rsiDailyClosePrice) {
        // Need 15 prices to calculate 14 price changes for RSI
        if (rsiDailyClosePrice.getPrices().size() < RSI_PERIOD + 1) {
            return;
        }

        double rsi = calculateRsi(rsiDailyClosePrice.getPriceValues());
        String displayName = symbol.getName();
        if (symbol.getSymbolType() == SymbolType.STOCK) {
            displayName = symbol.getDisplayName();
        }

        double previousRsi = rsiDailyClosePrice.getPreviousRsi();
        double rsiDiff = rsi - previousRsi;

        String rsiDiffString = "";
        if (previousRsi != 0) {
            rsiDiffString = String.format("(%+.1f)", rsiDiff);
        }

        if (rsi >= 70) {
            log.info("RSI for {} is in overbought zone: {}", displayName, rsi);
            telegramClient.sendMessage(
                    String.format(
                            "🔴 RSI for %s is in overbought zone: %.2f %s",
                            displayName, rsi, rsiDiffString));
        } else if (rsi <= 30) {
            log.info("RSI for {} is in oversold zone: {}", displayName, rsi);
            telegramClient.sendMessage(
                    String.format(
                            "🟢 RSI for %s is in oversold zone: %.2f %s",
                            displayName, rsi, rsiDiffString));
        }
        rsiDailyClosePrice.setPreviousRsi(rsi);
    }

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
            log.debug(
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
