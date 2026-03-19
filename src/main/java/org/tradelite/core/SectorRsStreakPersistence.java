package org.tradelite.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Persists sector RS streak data to JSON file.
 *
 * <p>Tracks consecutive days of outperformance or underperformance vs SPY for each sector ETF. Data
 * is persisted to survive application restarts.
 */
@Slf4j
@Component
public class SectorRsStreakPersistence {

    private static final String DEFAULT_FILE_PATH = "config/sector-rs-streaks.json";

    private final ObjectMapper objectMapper;
    private final String filePath;

    public SectorRsStreakPersistence(
            ObjectMapper objectMapper,
            @Value("${tradebot.sector-rs-streaks.file-path:" + DEFAULT_FILE_PATH + "}")
                    String filePath) {
        this.objectMapper = objectMapper;
        this.filePath = filePath != null ? filePath : DEFAULT_FILE_PATH;
    }

    /**
     * Updates the streak for a sector based on current performance.
     *
     * <p>If the sector maintains the same direction (outperforming/underperforming), the streak is
     * incremented. If the direction flips, the streak resets to 1.
     *
     * @param symbol The sector ETF symbol
     * @param isOutperforming True if currently outperforming SPY
     * @param date The current date
     * @return The updated streak
     * @throws IOException if persistence fails
     */
    public SectorRsStreak updateStreak(String symbol, boolean isOutperforming, LocalDate date)
            throws IOException {
        Map<String, SectorRsStreak> streaks = loadStreaks();
        SectorRsStreak currentStreak = streaks.get(symbol);

        SectorRsStreak newStreak;
        if (currentStreak == null) {
            // First time tracking this sector
            newStreak = SectorRsStreak.newStreak(symbol, isOutperforming, date);
            log.info(
                    "Starting new streak for {}: {} day 1",
                    symbol,
                    isOutperforming ? "outperforming" : "underperforming");
        } else if (currentStreak.lastUpdated().equals(date)) {
            // Already updated today, return existing
            return currentStreak;
        } else {
            // Update existing streak
            newStreak = currentStreak.update(isOutperforming, date);
            if (isOutperforming != currentStreak.isOutperforming()) {
                log.info(
                        "Streak reset for {}: {} -> {} (day 1)",
                        symbol,
                        currentStreak.isOutperforming() ? "outperforming" : "underperforming",
                        isOutperforming ? "outperforming" : "underperforming");
            } else {
                log.info(
                        "Streak extended for {}: {} day {}",
                        symbol,
                        isOutperforming ? "outperforming" : "underperforming",
                        newStreak.streakDays());
            }
        }

        streaks.put(symbol, newStreak);
        saveStreaks(streaks);
        return newStreak;
    }

    /**
     * Gets the current streak for a sector.
     *
     * @param symbol The sector ETF symbol
     * @return Optional containing the streak, or empty if not tracked
     */
    public Optional<SectorRsStreak> getStreak(String symbol) {
        return Optional.ofNullable(loadStreaks().get(symbol));
    }

    /**
     * Gets all current streaks.
     *
     * @return Map of symbol to streak data
     */
    public Map<String, SectorRsStreak> getAllStreaks() {
        return loadStreaks();
    }

    /** Loads streak data from file. */
    protected Map<String, SectorRsStreak> loadStreaks() {
        File file = new File(filePath);
        if (!file.exists()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(file, new TypeReference<>() {});
        } catch (IOException e) {
            log.warn("Failed to load sector RS streaks: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /** Saves streak data to file. */
    protected void saveStreaks(Map<String, SectorRsStreak> streaks) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), streaks);
    }
}
