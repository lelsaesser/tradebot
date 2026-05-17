package org.tradelite.repository;

import java.util.Map;
import java.util.Optional;
import org.tradelite.core.SectorRsStreak;

/**
 * Repository interface for persisting sector RS streak data.
 *
 * <p>Tracks consecutive days of outperformance or underperformance vs SPY for each sector ETF.
 */
public interface SectorRsStreakRepository {

    /**
     * Saves or updates a sector RS streak.
     *
     * @param streak The streak data to persist
     */
    void save(SectorRsStreak streak);

    /**
     * Finds the streak for a specific sector symbol.
     *
     * @param symbol The sector ETF symbol
     * @return Optional containing the streak if found
     */
    Optional<SectorRsStreak> findBySymbol(String symbol);

    /**
     * Returns all persisted streaks, keyed by symbol.
     *
     * @return Map of symbol to streak data
     */
    Map<String, SectorRsStreak> findAll();
}
