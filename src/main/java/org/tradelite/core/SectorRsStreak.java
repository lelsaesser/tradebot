package org.tradelite.core;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;

/**
 * Tracks consecutive days of outperformance or underperformance vs SPY for a sector ETF.
 *
 * <p>The streak counter increments each day the sector maintains the same performance direction
 * (outperforming or underperforming SPY). When the direction flips, the counter resets to 1.
 *
 * @param symbol The sector ETF symbol (e.g., "XLK")
 * @param streakDays Number of consecutive days in current performance direction
 * @param isOutperforming True if currently outperforming SPY, false if underperforming
 * @param lastUpdated The date when this streak was last updated
 */
public record SectorRsStreak(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("streakDays") int streakDays,
        @JsonProperty("isOutperforming") boolean isOutperforming,
        @JsonProperty("lastUpdated") @JsonFormat(pattern = "yyyy-MM-dd") LocalDate lastUpdated) {

    /**
     * Creates a new streak starting at day 1.
     *
     * @param symbol The sector ETF symbol
     * @param isOutperforming True if outperforming SPY
     * @param date The current date
     * @return A new streak with 1 day
     */
    public static SectorRsStreak newStreak(String symbol, boolean isOutperforming, LocalDate date) {
        return new SectorRsStreak(symbol, 1, isOutperforming, date);
    }

    /**
     * Creates an updated streak, either incrementing or resetting based on direction change.
     *
     * @param currentlyOutperforming The current performance direction
     * @param date The current date
     * @return Updated streak record
     */
    public SectorRsStreak update(boolean currentlyOutperforming, LocalDate date) {
        // If same direction, increment streak
        if (currentlyOutperforming == isOutperforming) {
            return new SectorRsStreak(symbol, streakDays + 1, isOutperforming, date);
        }
        // Direction changed, reset to 1
        return new SectorRsStreak(symbol, 1, currentlyOutperforming, date);
    }
}
