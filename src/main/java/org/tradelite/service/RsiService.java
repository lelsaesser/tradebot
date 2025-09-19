package org.tradelite.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradelite.client.telegram.TelegramClient;
import org.tradelite.common.TickerSymbol;
import org.tradelite.repository.DailyPriceRepository;
import org.tradelite.service.model.DailyPrice;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@Service
public class RsiService {

    private static final int RSI_PERIOD = 14;
    private static final int MAX_PRICES_PER_SYMBOL = 200;

    private final TelegramClient telegramClient;
    private final DailyPriceRepository dailyPriceRepository;
    private final DataMigrationService dataMigrationService;

    @Autowired
    public RsiService(TelegramClient telegramClient, 
                      DailyPriceRepository dailyPriceRepository,
                      DataMigrationService dataMigrationService) {
        this.telegramClient = telegramClient;
        this.dailyPriceRepository = dailyPriceRepository;
        this.dataMigrationService = dataMigrationService;
        
        // Run migration if needed
        initializeService();
    }
    
    private void initializeService() {
        try {
            if (dataMigrationService.isMigrationNeeded()) {
                log.info("JSON data migration needed, starting migration...");
                dataMigrationService.migrateJsonDataToDatabase();
                log.info("Migration completed successfully");
            } else {
                log.info("No migration needed. {}", dataMigrationService.getMigrationStatus());
            }
        } catch (Exception e) {
            log.error("Failed to initialize RsiService", e);
            throw new RuntimeException("Service initialization failed", e);
        }
    }

    @Transactional
    public void addPrice(TickerSymbol symbol, double price, LocalDate date) {
        String symbolKey = symbol.getName();
        
        // Check for potential market holiday
        if (isPotentialMarketHoliday(symbolKey, price, date)) {
            log.info("Potential market holiday detected for {}: price {} on {} is identical to previous trading day. Skipping price update.", 
                    symbol, price, date);
            return;
        }
        
        // Save or update price in database
        Optional<DailyPrice> existingPrice = dailyPriceRepository.findBySymbolAndDate(symbolKey, date);
        if (existingPrice.isPresent()) {
            // Update existing price
            DailyPrice dailyPrice = existingPrice.get();
            dailyPrice.setPrice(price);
            dailyPriceRepository.save(dailyPrice);
            log.debug("Updated price for {} on {} to {}", symbol, date, price);
        } else {
            // Add new price
            DailyPrice dailyPrice = new DailyPrice(symbolKey, date, price);
            dailyPriceRepository.save(dailyPrice);
            log.debug("Added new price for {} on {} with value {}", symbol, date, price);
        }
        
        // Clean up old prices (keep only latest 200)
        cleanupOldPrices(symbolKey);
        
        // Calculate and notify RSI
        calculateAndNotifyRsi(symbol);
    }

    private boolean isPotentialMarketHoliday(String symbol, double newPrice, LocalDate newDate) {
        List<DailyPrice> recentPrices = dailyPriceRepository.findBySymbolOrderByDateDesc(symbol);
        
        if (recentPrices.isEmpty()) {
            return false;
        }

        DailyPrice mostRecentPrice = recentPrices.get(0);
        double epsilon = 0.0001;
        boolean pricesAreIdentical = Math.abs(newPrice - mostRecentPrice.getPrice()) < epsilon;

        return pricesAreIdentical && !newDate.isEqual(mostRecentPrice.getDate());
    }

    private void calculateAndNotifyRsi(TickerSymbol symbol) {
        String symbolKey = symbol.getName();
        List<DailyPrice> prices = dailyPriceRepository.findLatest200PricesBySymbol(symbolKey);
        
        // Need 15 prices to calculate 14 price changes for RSI
        if (prices.size() < RSI_PERIOD + 1) {
            log.debug("Not enough prices for RSI calculation for {}: {} prices available", symbol, prices.size());
            return;
        }

        // Sort prices by date ascending for RSI calculation
        prices.sort(Comparator.comparing(DailyPrice::getDate));
        
        // Extract price values
        List<Double> priceValues = prices.stream()
                .map(DailyPrice::getPrice)
                .toList();

        double rsi = calculateRsi(priceValues);

        if (rsi >= 70) {
            log.info("RSI for {} is in overbought zone: {}", symbol, rsi);
            telegramClient.sendMessage(String.format("RSI for %s is in overbought zone: %.2f", symbol, rsi));
        } else if (rsi <= 30) {
            log.info("RSI for {} is in oversold zone: {}", symbol, rsi);
            telegramClient.sendMessage(String.format("RSI for %s is in oversold zone: %.2f", symbol, rsi));
        }
    }
    
    private void cleanupOldPrices(String symbol) {
        long totalPrices = dailyPriceRepository.countBySymbol(symbol);
        if (totalPrices > MAX_PRICES_PER_SYMBOL) {
            // Get the oldest prices to delete
            List<DailyPrice> allPrices = dailyPriceRepository.findBySymbolOrderByDateDesc(symbol);
            if (allPrices.size() > MAX_PRICES_PER_SYMBOL) {
                List<DailyPrice> pricesToDelete = allPrices.subList(MAX_PRICES_PER_SYMBOL, allPrices.size());
                dailyPriceRepository.deleteAll(pricesToDelete);
                log.debug("Cleaned up {} old prices for symbol {}", pricesToDelete.size(), symbol);
            }
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

    /**
     * Get all symbols that have price data in the database
     */
    public List<String> getAllSymbolsWithPriceData() {
        return dailyPriceRepository.findAllUniqueSymbols();
    }
    
    /**
     * Get price history for a specific symbol
     */
    public List<DailyPrice> getPriceHistory(String symbol) {
        return dailyPriceRepository.findBySymbolOrderByDateAsc(symbol);
    }
    
    /**
     * Get the latest N prices for a symbol
     */
    public List<DailyPrice> getLatestPrices(String symbol, int limit) {
        List<DailyPrice> prices = dailyPriceRepository.findBySymbolOrderByDateDesc(symbol);
        return prices.size() > limit ? prices.subList(0, limit) : prices;
    }
}
