package org.tradelite.repository;

import java.util.Optional;

/**
 * Repository interface for {@link TreasuryIndicatorState}. Stores per-series state for the US
 * Treasury macro indicators tracked by {@code TreasuryTracker} (#516) so that band transitions can
 * be detected across runs and survive bot restarts.
 *
 * <p>Two-method surface: {@code save} (INSERT OR REPLACE per series) and {@code findBySeriesId}. No
 * bulk operations needed — there are only four series.
 */
public interface TreasuryIndicatorStateRepository {

    /** Saves or replaces the state row for the given series. */
    void save(TreasuryIndicatorState state);

    /** Returns the most recent state for the given series, or empty if none exists yet. */
    Optional<TreasuryIndicatorState> findBySeriesId(String seriesId);
}
