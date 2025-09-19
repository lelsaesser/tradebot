package org.tradelite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.tradelite.repository.DailyPriceRepository;
import org.tradelite.service.model.DailyPrice;
import org.tradelite.service.model.RsiDailyClosePrice;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class DataMigrationService {
    
    private static final String RSI_DATA_FILE = "config/rsi-data.json";
    private static final String RSI_DATA_BACKUP_FILE = "config/rsi-data-backup.json";
    
    private final DailyPriceRepository dailyPriceRepository;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public DataMigrationService(DailyPriceRepository dailyPriceRepository, ObjectMapper objectMapper) {
        this.dailyPriceRepository = dailyPriceRepository;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Migrate existing JSON data to SQLite database
     */
    @Transactional
    public void migrateJsonDataToDatabase() {
        File jsonFile = new File(RSI_DATA_FILE);
        
        if (!fileExists(jsonFile)) {
            log.info("No existing JSON data file found at {}, skipping migration", RSI_DATA_FILE);
            return;
        }
        
        try {
            log.info("Starting migration of JSON data to SQLite database...");
            
            // Load existing JSON data
            Map<String, RsiDailyClosePrice> priceHistory = loadJsonData(jsonFile);
            
            if (priceHistory.isEmpty()) {
                log.info("No price data found in JSON file, migration complete");
                return;
            }
            
            int totalPricesMigrated = 0;
            
            // Migrate each symbol's price history
            for (Map.Entry<String, RsiDailyClosePrice> entry : priceHistory.entrySet()) {
                String symbol = entry.getKey();
                RsiDailyClosePrice rsiPrices = entry.getValue();
                
                if (rsiPrices.getPrices() != null && !rsiPrices.getPrices().isEmpty()) {
                    int pricesForSymbol = migratePricesForSymbol(symbol, rsiPrices.getPrices());
                    totalPricesMigrated += pricesForSymbol;
                    log.info("Migrated {} prices for symbol {}", pricesForSymbol, symbol);
                }
            }
            
            log.info("Migration completed successfully. Total prices migrated: {}", totalPricesMigrated);
            
            // Create backup of original JSON file
            createBackupOfJsonFile(jsonFile);
            
        } catch (Exception e) {
            log.error("Error during data migration", e);
            throw new IllegalStateException("Failed to migrate JSON data to database", e);
        }
    }
    
    /**
     * Check if file exists - can be overridden for testing
     */
    protected boolean fileExists(File file) {
        return file.exists();
    }
    
    /**
     * Migrate prices for a specific symbol, handling duplicates gracefully
     */
    private int migratePricesForSymbol(String symbol, List<org.tradelite.service.model.DailyPrice> prices) {
        int migratedCount = 0;
        
        for (org.tradelite.service.model.DailyPrice price : prices) {
            try {
                // Check if price already exists
                if (dailyPriceRepository.findBySymbolAndDate(symbol, price.getDate()).isEmpty()) {
                    DailyPrice dbPrice = new DailyPrice(symbol, price.getDate(), price.getPrice());
                    dailyPriceRepository.save(dbPrice);
                    migratedCount++;
                } else {
                    log.debug("Price for {} on {} already exists, skipping", symbol, price.getDate());
                }
            } catch (Exception e) {
                log.warn("Failed to migrate price for {} on {}: {}", symbol, price.getDate(), e.getMessage());
            }
        }
        
        return migratedCount;
    }
    
    /**
     * Load JSON data from file
     */
    private Map<String, RsiDailyClosePrice> loadJsonData(File jsonFile) throws IOException {
        try {
            return objectMapper.readValue(jsonFile, 
                objectMapper.getTypeFactory().constructMapType(HashMap.class, String.class, RsiDailyClosePrice.class));
        } catch (IOException e) {
            log.error("Failed to load JSON data from {}", jsonFile.getPath(), e);
            throw e;
        }
    }
    
    /**
     * Create backup of original JSON file
     */
    private void createBackupOfJsonFile(File originalFile) {
        try {
            File backupFile = new File(RSI_DATA_BACKUP_FILE);
            if (backupFile.exists()) {
                log.info("Backup file {} already exists, skipping backup creation", RSI_DATA_BACKUP_FILE);
                return;
            }
            
            objectMapper.writeValue(backupFile, loadJsonData(originalFile));
            log.info("Created backup of original JSON data at {}", RSI_DATA_BACKUP_FILE);
            
        } catch (IOException e) {
            log.warn("Failed to create backup of JSON file: {}", e.getMessage());
        }
    }
    
    /**
     * Check if migration is needed (JSON file exists and database is empty)
     */
    public boolean isMigrationNeeded() {
        File jsonFile = new File(RSI_DATA_FILE);
        boolean hasJsonData = jsonFile.exists() && jsonFile.length() > 0;
        boolean hasDatabaseData = dailyPriceRepository.count() > 0;
        
        return hasJsonData && !hasDatabaseData;
    }
    
    /**
     * Get migration status information
     */
    public String getMigrationStatus() {
        File jsonFile = new File(RSI_DATA_FILE);
        long dbRecords = dailyPriceRepository.count();
        
        return String.format("JSON file exists: %s, Database records: %d", 
            jsonFile.exists(), dbRecords);
    }
}
