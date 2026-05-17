package org.tradelite.core;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.tradelite.quant.EmaAnalysis;
import org.tradelite.quant.TrendDirection;
import org.tradelite.quant.VfiAnalysis;
import org.tradelite.service.RsTrendResult;

/**
 * Evaluates whether a stock shows signs of institutional accumulation.
 *
 * <p>Conditions (all must be true):
 *
 * <ul>
 *   <li>Price weak: EMA9 < EMA21 (no established uptrend)
 *   <li>Volume bullish: VFI > 0 AND VFI > signal line (net buying pressure accelerating)
 * </ul>
 *
 * <p>RS vs SPY is included as informational context but is not a gating condition.
 */
@Service
public class AccumulationDetectionService {

    /**
     * Evaluates whether the given indicator state represents institutional accumulation.
     *
     * @param symbol Ticker symbol
     * @param displayName Human-readable name
     * @param ema EMA analysis (must contain valid ema9 and ema21)
     * @param vfi VFI analysis (must have positive VFI above signal line)
     * @param rsTrend RS trend result (nullable — used for informational context only)
     * @return Signal if accumulation conditions are met, empty otherwise
     */
    public Optional<AccumulationSignal> evaluate(
            String symbol,
            String displayName,
            EmaAnalysis ema,
            VfiAnalysis vfi,
            RsTrendResult rsTrend) {

        // Gate 1: EMA9 and EMA21 must be available
        if (Double.isNaN(ema.ema9()) || Double.isNaN(ema.ema21())) {
            return Optional.empty();
        }

        // Gate 2: Price must be weak — EMA9 strictly below EMA21
        if (ema.ema9() >= ema.ema21()) {
            return Optional.empty();
        }

        // Gate 3: VFI must be positive (net buying pressure)
        if (vfi.vfiValue() <= 0) {
            return Optional.empty();
        }

        // Gate 4: VFI must be above signal line (accelerating)
        if (vfi.vfiValue() <= vfi.signalLineValue()) {
            return Optional.empty();
        }

        // All conditions met — build signal with RS context
        double rsValue = rsTrend != null ? rsTrend.rsValue() : 0;
        double rsEma = rsTrend != null ? rsTrend.rsEma() : 0;
        TrendDirection rsTrendDir = rsTrend != null ? rsTrend.rsTrend() : TrendDirection.FLAT;
        TrendDirection rsEmaTrendDir = rsTrend != null ? rsTrend.rsEmaTrend() : TrendDirection.FLAT;

        return Optional.of(
                new AccumulationSignal(
                        symbol,
                        displayName,
                        ema.currentPrice(),
                        ema.ema9(),
                        ema.ema21(),
                        vfi.vfiValue(),
                        vfi.signalLineValue(),
                        rsValue,
                        rsEma,
                        rsTrendDir,
                        rsEmaTrendDir));
    }
}
