package org.tradelite.core;

import org.tradelite.quant.TrendDirection;

/**
 * Signal emitted when institutional accumulation is detected: price action is weak (EMA9 < EMA21)
 * while volume flow is bullish and accelerating (VFI positive and above signal line).
 *
 * @param symbol Ticker symbol
 * @param displayName Human-readable company name
 * @param currentPrice Latest closing price
 * @param ema9 9-period EMA
 * @param ema21 21-period EMA
 * @param vfiValue Volume Flow Indicator value
 * @param vfiSignalLine VFI signal line (5-period EMA of VFI)
 * @param rsValue Relative strength vs SPY (informational)
 * @param rsEma EMA of RS (informational)
 * @param rsTrend Slope direction of RS value
 * @param rsEmaTrend Slope direction of RS EMA
 */
public record AccumulationSignal(
        String symbol,
        String displayName,
        double currentPrice,
        double ema9,
        double ema21,
        double vfiValue,
        double vfiSignalLine,
        double rsValue,
        double rsEma,
        TrendDirection rsTrend,
        TrendDirection rsEmaTrend) {}
