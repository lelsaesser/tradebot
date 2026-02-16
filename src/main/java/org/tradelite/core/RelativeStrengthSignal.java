package org.tradelite.core;

/**
 * Represents a relative strength crossover signal for a stock vs SPY benchmark.
 *
 * @param symbol The stock ticker symbol
 * @param displayName The display name of the stock
 * @param signalType The type of signal (OUTPERFORMING or UNDERPERFORMING)
 * @param rsValue The current relative strength value (stock price / SPY price ratio)
 * @param emaValue The current EMA value of the RS
 * @param percentageDiff The percentage difference between RS and EMA
 */
public record RelativeStrengthSignal(
        String symbol,
        String displayName,
        SignalType signalType,
        double rsValue,
        double emaValue,
        double percentageDiff) {

    public enum SignalType {
        /** Stock is outperforming SPY benchmark (RS crossed above EMA) */
        OUTPERFORMING,
        /** Stock is underperforming SPY benchmark (RS crossed below EMA) */
        UNDERPERFORMING
    }
}
