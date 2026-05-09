package org.tradelite.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.tradelite.quant.EmaAnalysis;
import org.tradelite.quant.EmaSignalType;
import org.tradelite.quant.TrendDirection;
import org.tradelite.quant.VfiAnalysis;
import org.tradelite.service.RsTrendResult;

class AccumulationDetectionServiceTest {

    private AccumulationDetectionService service;

    @BeforeEach
    void setUp() {
        service = new AccumulationDetectionService();
    }

    @Test
    void evaluate_allConditionsMet_returnsSignal() {
        EmaAnalysis ema = createEma(100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 3.5, 2.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isPresent();
        AccumulationSignal signal = result.get();
        assertThat(signal.symbol()).isEqualTo("AAPL");
        assertThat(signal.displayName()).isEqualTo("Apple");
        assertThat(signal.currentPrice()).isEqualTo(100.0);
        assertThat(signal.ema9()).isEqualTo(98.0);
        assertThat(signal.ema21()).isEqualTo(102.0);
        assertThat(signal.vfiValue()).isEqualTo(3.5);
        assertThat(signal.vfiSignalLine()).isEqualTo(2.0);
        assertThat(signal.rsValue()).isEqualTo(1.05);
        assertThat(signal.rsEma()).isEqualTo(1.03);
        assertThat(signal.rsTrend()).isEqualTo(TrendDirection.RISING);
        assertThat(signal.rsEmaTrend()).isEqualTo(TrendDirection.RISING);
    }

    @Test
    void evaluate_ema9AboveEma21_returnsEmpty() {
        // EMA9 > EMA21 means uptrend — not accumulation
        EmaAnalysis ema = createEma(105.0, 104.0, 100.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 3.5, 2.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_ema9EqualsEma21_returnsEmpty() {
        // EMA9 == EMA21 means not strictly weak — no signal
        EmaAnalysis ema = createEma(100.0, 102.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 3.5, 2.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.FLAT, TrendDirection.FLAT);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_vfiNegative_returnsEmpty() {
        EmaAnalysis ema = createEma(100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", -1.5, -2.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_vfiZero_returnsEmpty() {
        EmaAnalysis ema = createEma(100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 0.0, -1.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_vfiBelowSignalLine_returnsEmpty() {
        // VFI positive but below signal line — not accelerating
        EmaAnalysis ema = createEma(100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 2.0, 3.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_vfiEqualsSignalLine_returnsEmpty() {
        // VFI equals signal line — not strictly above
        EmaAnalysis ema = createEma(100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 2.5, 2.5);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.FLAT, TrendDirection.FLAT);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_ema9IsNaN_returnsEmpty() {
        EmaAnalysis ema = createEma(100.0, Double.NaN, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 3.5, 2.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_ema21IsNaN_returnsEmpty() {
        EmaAnalysis ema = createEma(100.0, 98.0, Double.NaN);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 3.5, 2.0);
        RsTrendResult rsTrend =
                new RsTrendResult(1.05, 1.03, TrendDirection.RISING, TrendDirection.RISING);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, rsTrend);

        assertThat(result).isEmpty();
    }

    @Test
    void evaluate_rsTrendNull_usesDefaults() {
        EmaAnalysis ema = createEma(100.0, 98.0, 102.0);
        VfiAnalysis vfi = new VfiAnalysis("AAPL", "Apple", 3.5, 2.0);

        Optional<AccumulationSignal> result = service.evaluate("AAPL", "Apple", ema, vfi, null);

        assertThat(result).isPresent();
        AccumulationSignal signal = result.get();
        assertThat(signal.rsValue()).isZero();
        assertThat(signal.rsEma()).isZero();
        assertThat(signal.rsTrend()).isEqualTo(TrendDirection.FLAT);
        assertThat(signal.rsEmaTrend()).isEqualTo(TrendDirection.FLAT);
    }

    private EmaAnalysis createEma(double price, double ema9, double ema21) {
        return new EmaAnalysis(
                "AAPL", "Apple", price, ema9, ema21, 95.0, 90.0, 85.0, 5, 0, EmaSignalType.GREEN);
    }
}
