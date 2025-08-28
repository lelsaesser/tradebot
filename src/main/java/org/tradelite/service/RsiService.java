package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    private Map<TickerSymbol, RsiDailyClosePrice> priceHistory = new HashMap<>();

    @Autowired
    public RsiService(TelegramClient telegramClient, ObjectMapper objectMapper) {
        this.telegramClient = telegramClient;
        this.objectMapper = objectMapper;
        loadPriceHistory();
    }

    public void addPrice(TickerSymbol symbol, double price, LocalDate date) {
        RsiDailyClosePrice rsiDailyClosePrice = priceHistory.getOrDefault(symbol, new RsiDailyClosePrice());
        rsiDailyClosePrice.addPrice(date, price);
        priceHistory.put(symbol, rsiDailyClosePrice);
        savePriceHistory();
        calculateAndNotifyRsi(symbol, rsiDailyClosePrice);
    }

    private void calculateAndNotifyRsi(TickerSymbol symbol, RsiDailyClosePrice rsiDailyClosePrice) {
        if (rsiDailyClosePrice.getPrices().size() < RSI_PERIOD) {
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

    private double calculateRsi(List<Double> prices) {
        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

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

        double avgGain = gains.stream().mapToDouble(d -> d).average().orElse(0.0);
        double avgLoss = losses.stream().mapToDouble(d -> d).average().orElse(0.0);

        if (avgLoss == 0) {
            return 100;
        }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private void loadPriceHistory() {
        try {
            File file = new File(RSI_DATA_FILE);
            if (file.exists()) {
                priceHistory = objectMapper.readValue(file, objectMapper.getTypeFactory().constructMapType(HashMap.class, TickerSymbol.class, RsiDailyClosePrice.class));
            }
        } catch (IOException e) {
            log.error("Error loading RSI data", e);
        }
    }

    private void savePriceHistory() {
        try {
            objectMapper.writeValue(new File(RSI_DATA_FILE), priceHistory);
        } catch (IOException e) {
            log.error("Error saving RSI data", e);
        }
    }
}
