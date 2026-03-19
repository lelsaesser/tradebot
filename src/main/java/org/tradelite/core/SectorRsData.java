package org.tradelite.core;

/**
 * Represents a sector ETF's relative strength data for sorting and display.
 *
 * @param symbol The sector ETF symbol (e.g., "XLK")
 * @param displayName The human-readable sector name (e.g., "Technology")
 * @param rsValue The current RS (sector/SPY ratio)
 * @param emaValue The 50-period EMA of the RS ratio
 * @param percentageDiff The percentage deviation of RS from EMA
 * @param dataPoints The number of data points used in calculation
 * @param isComplete Whether enough data points exist for reliable EMA
 * @param streakDays Consecutive days of current performance direction
 */
public record SectorRsData(
        String symbol,
        String displayName,
        double rsValue,
        double emaValue,
        double percentageDiff,
        int dataPoints,
        boolean isComplete,
        int streakDays) {}
