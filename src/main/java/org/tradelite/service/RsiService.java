package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.TickerSymbol;
import org.tradelite.service.model.RsiDailyClosePrice;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        rsiDailyClosePrice.addPrice(date, price);
        priceHistory.put(symbolKey, rsiDailyClosePrice);
        savePriceHistory();
        calculateAndNotifyRsi(symbol, rsiDailyClosePrice);
    }

    private void calculateAndNotifyRsi(TickerSymbol symbol, RsiDailyClosePrice rsiDailyClosePrice) {
        // Need 15 prices to calculate 14 price changes for RSI
        if (rsiDailyClosePrice.getPrices().size() < RSI_PERIOD + 1) {
            return;
        }

        double rsi = calculateRsi(rsiDailyClosePrice.getPriceValues());

        if (rsi >= 70) {
            log.info("RSI for {} is in overbought zone: {}", symbol, rsi);
            telegramClient.sendMessage(String.format("RSI for %s is in overbought zone: %.2f", symbol, rsi));
        } else if (rsi <= 30) {
            log.info("RSI for {} is in oversold zone: {}", symbol, rsi);
            telegramClient.sendMessage(String.format("RSI for %s is in oversold zone: %.2f", symbol, rsi));
        }
    }

    protected double calculateRsi(List<Double> prices) {
        if (prices.size() < RSI_PERIOD + 1) {
            throw new IllegalArgumentException("Need at least " + (RSI_PERIOD + 1) + " prices to calculate RSI");
        }

        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        // Calculate price changes
        for (int i = 1; i < prices.size(); i++) {
            double change = prices.get(i) - prices.get(i - 1);
            if (change > 0) {
                gains.add(change);
                losses.add(0.0);
            } else {
                gains.add(0.0);
                losses.add(Math.abs(change));
            }
        }

        // Calculate initial average gain and loss (simple average for first RSI_PERIOD)
        double initialAvgGain = 0.0;
        double initialAvgLoss = 0.0;
        
        for (int i = 0; i < RSI_PERIOD; i++) {
            initialAvgGain += gains.get(i);
            initialAvgLoss += losses.get(i);
        }
        
        initialAvgGain /= RSI_PERIOD;
        initialAvgLoss /= RSI_PERIOD;

        // Use Wilder's smoothing for subsequent calculations
        double avgGain = initialAvgGain;
        double avgLoss = initialAvgLoss;
        
        // Apply Wilder's smoothing for any additional periods beyond the initial 14
        for (int i = RSI_PERIOD; i < gains.size(); i++) {
            avgGain = ((avgGain * (RSI_PERIOD - 1)) + gains.get(i)) / RSI_PERIOD;
            avgLoss = ((avgLoss * (RSI_PERIOD - 1)) + losses.get(i)) / RSI_PERIOD;
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
