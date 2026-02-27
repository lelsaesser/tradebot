package org.tradelite.core;

/**
 * Represents a momentum signal detected for a sector ETF using Rate of Change (ROC) analysis.
 *
 * <p>Momentum is measured using ROC over 10-day and 20-day periods. Signals are generated when
 * momentum crosses zero (direction change), indicating a shift in price trend.
 *
 * @param symbol The ticker symbol (e.g., "XLK")
 * @param displayName The human-readable sector name (e.g., "Technology")
 * @param signalType The type of momentum signal detected
 * @param roc10 Current 10-day Rate of Change percentage
 * @param roc20 Current 20-day Rate of Change percentage
 * @param previousRoc10 Previous day's 10-day ROC (for crossover detection)
 * @param previousRoc20 Previous day's 20-day ROC (for crossover detection)
 */
public record MomentumRocSignal(
        String symbol,
        String displayName,
        SignalType signalType,
        double roc10,
        double roc20,
        double previousRoc10,
        double previousRoc20) {

    /**
     * Types of momentum signals that can be detected.
     *
     * <p>TURNING_POSITIVE and TURNING_NEGATIVE indicate zero-line crossovers in the 10-day ROC,
     * which signal momentum direction changes.
     */
    public enum SignalType {
        /** ROC crossed from negative to positive - momentum turning bullish */
        MOMENTUM_TURNING_POSITIVE,

        /** ROC crossed from positive to negative - momentum turning bearish */
        MOMENTUM_TURNING_NEGATIVE
    }
}
