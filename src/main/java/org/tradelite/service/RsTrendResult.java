package org.tradelite.service;

import org.tradelite.quant.TrendDirection;

/**
 * Holds the current RS value and EMA with their respective slope directions.
 *
 * @param rsValue Current relative strength value (stock price / SPY price)
 * @param rsEma Current EMA of the RS series
 * @param rsTrend Slope direction of the RS value over the lookback period
 * @param rsEmaTrend Slope direction of the RS EMA over the lookback period
 */
public record RsTrendResult(
        double rsValue, double rsEma, TrendDirection rsTrend, TrendDirection rsEmaTrend) {}
