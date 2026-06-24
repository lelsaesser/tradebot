package org.tradelite.repository;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Persisted state for a single Treasury macro indicator series. Stored one row per FRED series in
 * the {@code treasury_indicator_state} table. {@code TreasuryTracker} reads this on each tick,
 * compares with today's classification to detect band transitions, then writes the new state back.
 *
 * <p>{@code initialized=false} on first write suppresses ghost-transition alerts on the first run
 * after a deploy — see {@link org.tradelite.repository.MomentumRocRepository} which uses the same
 * pattern.
 *
 * @param seriesId FRED series ID (e.g. {@code "T10Y3M"})
 * @param lastBand the enum name of the band classification at {@code lastObservationDate}
 * @param lastValue the raw observation value at that date
 * @param lastObservationDate the FRED observation date this state reflects
 * @param initialized {@code false} until at least one prior run has been recorded; suppresses the
 *     transition alert on the first observation
 * @param updatedAt the bot's wall-clock at write time, for forensics
 */
public record TreasuryIndicatorState(
        String seriesId,
        String lastBand,
        double lastValue,
        LocalDate lastObservationDate,
        boolean initialized,
        Instant updatedAt) {}
