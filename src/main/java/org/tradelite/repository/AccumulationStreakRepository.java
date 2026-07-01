package org.tradelite.repository;

import java.util.Optional;
import java.util.Set;
import org.tradelite.core.AccumulationStreak;

/**
 * Repository interface for persisting accumulation streak data.
 *
 * <p>Tracks consecutive days of institutional accumulation signal for each stock.
 */
public interface AccumulationStreakRepository {

    /**
     * Saves or updates an accumulation streak.
     *
     * @param streak The streak data to persist
     */
    void save(AccumulationStreak streak);

    /**
     * Finds the streak for a specific stock symbol.
     *
     * @param symbol The stock ticker symbol
     * @return Optional containing the streak if found
     */
    Optional<AccumulationStreak> findBySymbol(String symbol);

    /**
     * Deletes all streaks except for the specified symbols. If the set is empty, deletes all
     * streaks.
     *
     * @param symbols Symbols to keep
     */
    void deleteAllExcept(Set<String> symbols);

    /**
     * Deletes all rows for the given symbol.
     *
     * @param symbol The stock ticker symbol
     * @return Number of rows deleted
     */
    int deleteBySymbol(String symbol);
}
