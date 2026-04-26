package org.tradelite.core;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.repository.SectorRsStreakRepository;

/**
 * Persists sector RS streak data to SQLite.
 *
 * <p>Tracks consecutive days of outperformance or underperformance vs SPY for each sector ETF. Data
 * is persisted to survive application restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectorRsStreakPersistence {

    private final SectorRsStreakRepository sectorRsStreakRepository;

    /**
     * Result of a streak update, carrying both the new streak and information about whether the
     * previous streak ended.
     *
     * @param newStreak The updated streak record
     * @param previousStreakDays The number of days in the previous streak (0 if no direction
     *     change)
     * @param directionChanged True if the performance direction flipped
     */
    public record StreakUpdateResult(
            SectorRsStreak newStreak, int previousStreakDays, boolean directionChanged) {}

    /**
     * Updates the streak for a sector based on current performance.
     *
     * <p>If the sector maintains the same direction (outperforming/underperforming), the streak is
     * incremented. If the direction flips, the streak resets to 1.
     *
     * @param symbol The sector ETF symbol
     * @param isOutperforming True if currently outperforming SPY
     * @param date The current date
     * @return The update result containing the new streak and direction change info
     */
    public StreakUpdateResult updateStreak(String symbol, boolean isOutperforming, LocalDate date) {
        Optional<SectorRsStreak> currentStreakOpt = sectorRsStreakRepository.findBySymbol(symbol);

        SectorRsStreak newStreak;
        int previousStreakDays = 0;
        boolean directionChanged = false;

        if (currentStreakOpt.isEmpty()) {
            // First time tracking this sector
            newStreak = SectorRsStreak.newStreak(symbol, isOutperforming, date);
            log.info(
                    "Starting new streak for {}: {} day 1",
                    symbol,
                    isOutperforming ? "outperforming" : "underperforming");
        } else {
            SectorRsStreak currentStreak = currentStreakOpt.get();
            if (currentStreak.lastUpdated().equals(date)) {
                // Already updated today, return existing
                return new StreakUpdateResult(currentStreak, 0, false);
            }
            // Update existing streak
            directionChanged = (isOutperforming != currentStreak.isOutperforming());
            if (directionChanged) {
                previousStreakDays = currentStreak.streakDays();
                log.info(
                        "Streak reset for {}: {} -> {} (was {} days, now day 1)",
                        symbol,
                        currentStreak.isOutperforming() ? "outperforming" : "underperforming",
                        isOutperforming ? "outperforming" : "underperforming",
                        previousStreakDays);
            } else {
                log.info(
                        "Streak extended for {}: {} day {}",
                        symbol,
                        isOutperforming ? "outperforming" : "underperforming",
                        currentStreak.streakDays() + 1);
            }
            newStreak = currentStreak.update(isOutperforming, date);
        }

        sectorRsStreakRepository.save(newStreak);
        return new StreakUpdateResult(newStreak, previousStreakDays, directionChanged);
    }

    /**
     * Gets the current streak for a sector.
     *
     * @param symbol The sector ETF symbol
     * @return Optional containing the streak, or empty if not tracked
     */
    public Optional<SectorRsStreak> getStreak(String symbol) {
        return sectorRsStreakRepository.findBySymbol(symbol);
    }

    /**
     * Gets all current streaks.
     *
     * @return Map of symbol to streak data
     */
    public Map<String, SectorRsStreak> getAllStreaks() {
        return sectorRsStreakRepository.findAll();
    }
}
