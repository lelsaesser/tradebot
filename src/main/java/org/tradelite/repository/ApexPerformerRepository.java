package org.tradelite.repository;

import java.util.Set;

/**
 * Repository for the apex performer set — stocks currently outperforming the top sector ETF.
 *
 * <p>The set is refreshed twice daily by the sector RS daily summary cron and read at intra-day
 * pullback alert time to highlight the strongest signals.
 */
public interface ApexPerformerRepository {

    /**
     * Atomically replaces the persisted set with the given symbols. Symbols not in {@code symbols}
     * are removed.
     *
     * @param symbols The complete current apex performer set
     */
    void replaceAll(Set<String> symbols);

    /**
     * Returns all currently persisted apex performer symbols.
     *
     * @return Set of tickers
     */
    Set<String> findAll();
}
