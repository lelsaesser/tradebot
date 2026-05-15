package org.tradelite.core;

import java.time.LocalDate;

/**
 * Tracks consecutive days of institutional accumulation signal for a stock.
 *
 * @param symbol Ticker symbol
 * @param streakDays Number of consecutive days the accumulation signal has been active
 * @param lastUpdated Date when the streak was last updated
 */
public record AccumulationStreak(String symbol, int streakDays, LocalDate lastUpdated) {}
