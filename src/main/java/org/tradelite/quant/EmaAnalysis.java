package org.tradelite.quant;

/**
 * Result of EMA analysis for a single symbol.
 *
 * <p>Contains the current price, available EMA values (9, 21, 50, 100, 200 day), the number of EMAs
 * the price is below, and the signal classification. EMA values are {@code Double.NaN} when
 * insufficient data is available for that period.
 *
 * @param symbol The stock ticker symbol
 * @param displayName Human-readable name for the symbol
 * @param currentPrice Latest closing price
 * @param ema9 9-day Exponential Moving Average, or NaN if unavailable
 * @param ema21 21-day Exponential Moving Average, or NaN if unavailable
 * @param ema50 50-day Exponential Moving Average, or NaN if unavailable
 * @param ema100 100-day Exponential Moving Average, or NaN if unavailable
 * @param ema200 200-day Exponential Moving Average, or NaN if unavailable
 * @param emasAvailable Number of EMAs that could be calculated (0-5)
 * @param emasBelow Number of available EMAs the current price is below
 * @param signalType Signal classification (GREEN, YELLOW, RED)
 */
public record EmaAnalysis(
        String symbol,
        String displayName,
        double currentPrice,
        double ema9,
        double ema21,
        double ema50,
        double ema100,
        double ema200,
        int emasAvailable,
        int emasBelow,
        EmaSignalType signalType) {}
