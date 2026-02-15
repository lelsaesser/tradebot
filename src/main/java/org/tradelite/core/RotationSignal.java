package org.tradelite.core;

import java.math.BigDecimal;

/**
 * Represents a detected sector rotation signal.
 *
 * @param sectorName The name of the industry sector
 * @param signalType The type of rotation signal (ROTATING_IN or ROTATING_OUT)
 * @param weeklyPerformance The current weekly performance percentage
 * @param monthlyPerformance The current monthly performance percentage
 * @param zScoreWeekly The z-score for weekly performance (how many std devs from mean)
 * @param zScoreMonthly The z-score for monthly performance
 * @param confidence The confidence level of the signal (HIGH, MEDIUM, LOW)
 */
public record RotationSignal(
        String sectorName,
        SignalType signalType,
        BigDecimal weeklyPerformance,
        BigDecimal monthlyPerformance,
        double zScoreWeekly,
        double zScoreMonthly,
        Confidence confidence) {

    public enum SignalType {
        ROTATING_IN, // Sector gaining momentum (money flowing in)
        ROTATING_OUT // Sector losing momentum (money flowing out)
    }

    public enum Confidence {
        HIGH, // Both weekly and monthly z-scores exceed threshold
        MEDIUM, // Only one timeframe exceeds threshold
        LOW // Weaker signal
    }
}
