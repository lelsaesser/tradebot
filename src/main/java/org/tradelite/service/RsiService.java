package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.TickerSymbol;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.RsiDailyClosePrice;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class RsiService {

    private static final int RSI_PERIOD = 14;
    private static final String RSI_DATA_FILE = "config/rsi-data.json";

    private final TelegramClient telegramClient;
    private final ObjectMapper objectMapper;

    @Getter
    private Map<String, RsiDailyClosePrice> priceHistory = new HashMap<>();

    @Autowired
    public RsiService(TelegramClient telegramClient, ObjectMapper objectMapper) throws IOException {
        this.telegramClient = telegramClient;
        this.objectMapper = objectMapper;
        loadPriceHistory();
    }

    public void addPrice(TickerSymbol symbol, double price, LocalDate date) throws IOException {
        String symbolKey = symbol.getName();
        RsiDailyClosePrice rsiDailyClosePrice = priceHistory.getOrDefault(symbolKey, new RsiDailyClosePrice());
        
        if (isPotentialMarketHoliday(rsiDailyClosePrice, price, date)) {
            log.info("Potential market holiday detected for {}: price {} on {} is identical to previous trading day. Skipping price update.", 
                    symbol, price, date);
            return;
        }
        
        rsiDailyClosePrice.addPrice(date, price);
        priceHistory.put(symbolKey, rsiDailyClosePrice);
        savePriceHistory();
        calculateAndNotifyRsi(symbol, rsiDailyClosePrice);
    }

    private boolean isPotentialMarketHoliday(RsiDailyClosePrice rsiDailyClosePrice, double newPrice, LocalDate newDate) {
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

        if (rsi >= 70) {
            log.info("RSI for {} is in overbought zone: {}", symbol, rsi);
            telegramClient.sendMessage(String.format("🔴 RSI for %s is in overbought zone: %.2f", symbol, rsi));
        } else if (rsi <= 30) {
            log.info("RSI for {} is in oversold zone: {}", symbol, rsi);
            telegramClient.sendMessage(String.format("🟢 RSI for %s is in oversold zone: %.2f", symbol, rsi));
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

    protected void loadPriceHistory() throws IOException {
        try {
            File file = new File(RSI_DATA_FILE);
            if (file.exists()) {
                priceHistory = objectMapper.readValue(file, objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, RsiDailyClosePrice.class));
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
