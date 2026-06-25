package org.tradelite.repository;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Persisted state for a single Treasury macro indicator series. Stored one row per FRED series in
 * the {@code treasury_indicator_state} table. {@code TreasuryTracker} reads this on each tick,
 * compares with today's classification to detect band transitions, then writes the new state back.
 *
 * <p>First-run alert suppression is handled by the absence of a row, not by the {@code initialized}
 * flag — {@code TreasuryTracker} skips the alert when no prior row exists, then writes a row with
 * {@code initialized=true} for future comparison. The flag exists as a hatch for manually-seeded
 * rows (matching the {@code momentum_roc_state} convention); production writes are always {@code
 * true}.
 *
 * @param seriesId FRED series ID (e.g. {@code "T10Y3M"})
 * @param lastBand the enum name of the band classification at {@code lastObservationDate}
 * @param lastValue the raw observation value at that date
 * @param lastObservationDate the FRED observation date this state reflects
 * @param initialized {@code true} for production-written rows; {@code false} reserved for
 *     manually-seeded rows that should suppress the next comparison (matches {@code
 *     momentum_roc_state})
 * @param updatedAt the bot's wall-clock at write time, for forensics
 */
public record TreasuryIndicatorState(
        String seriesId,
        String lastBand,
        double lastValue,
        LocalDate lastObservationDate,
        boolean initialized,
        Instant updatedAt) {}
