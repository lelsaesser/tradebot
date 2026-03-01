package org.tradelite.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.tradelite.core.MomentumRocSignal.SignalType;

class MomentumRocSignalTest {

    @Test
    void constructor_createsSignalWithAllValues() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.5,
                        3.3,
                        -2.2,
                        -1.1);

        assertEquals("XLK", signal.symbol());
        assertEquals("Technology", signal.displayName());
        assertEquals(SignalType.MOMENTUM_TURNING_POSITIVE, signal.signalType());
        assertEquals(5.5, signal.roc10());
        assertEquals(3.3, signal.roc20());
        assertEquals(-2.2, signal.previousRoc10());
        assertEquals(-1.1, signal.previousRoc20());
    }

    @Test
    void signalType_momentumTurningPositive() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLF",
                        "Financials",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        2.0,
                        1.5,
                        -1.0,
                        -0.5);

        assertEquals(SignalType.MOMENTUM_TURNING_POSITIVE, signal.signalType());
    }

    @Test
    void signalType_momentumTurningNegative() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLE",
                        "Energy",
                        SignalType.MOMENTUM_TURNING_NEGATIVE,
                        -2.0,
                        -1.5,
                        1.0,
                        0.5);

        assertEquals(SignalType.MOMENTUM_TURNING_NEGATIVE, signal.signalType());
    }

    @Test
    void equals_sameValues_returnsTrue() {
        MomentumRocSignal signal1 =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.0,
                        3.0,
                        -2.0,
                        -1.0);
        MomentumRocSignal signal2 =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.0,
                        3.0,
                        -2.0,
                        -1.0);

        assertEquals(signal1, signal2);
    }

    @Test
    void equals_differentValues_returnsFalse() {
        MomentumRocSignal signal1 =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.0,
                        3.0,
                        -2.0,
                        -1.0);
        MomentumRocSignal signal2 =
                new MomentumRocSignal(
                        "XLF",
                        "Financials",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.0,
                        3.0,
                        -2.0,
                        -1.0);

        assertNotEquals(signal1, signal2);
    }

    @Test
    void hashCode_sameValues_returnsSameHash() {
        MomentumRocSignal signal1 =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.0,
                        3.0,
                        -2.0,
                        -1.0);
        MomentumRocSignal signal2 =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.0,
                        3.0,
                        -2.0,
                        -1.0);

        assertEquals(signal1.hashCode(), signal2.hashCode());
    }

    @Test
    void toString_containsAllFields() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLK",
                        "Technology",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        5.5,
                        3.3,
                        -2.2,
                        -1.1);

        String str = signal.toString();

        assertTrue(str.contains("XLK"));
        assertTrue(str.contains("Technology"));
        assertTrue(str.contains("MOMENTUM_TURNING_POSITIVE"));
        assertTrue(str.contains("5.5"));
        assertTrue(str.contains("3.3"));
    }

    @Test
    void signalType_enum_hasBothValues() {
        SignalType[] values = SignalType.values();

        assertEquals(2, values.length);
        assertNotNull(SignalType.valueOf("MOMENTUM_TURNING_POSITIVE"));
        assertNotNull(SignalType.valueOf("MOMENTUM_TURNING_NEGATIVE"));
    }

    @Test
    void negativeRocValues_storedCorrectly() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLE",
                        "Energy",
                        SignalType.MOMENTUM_TURNING_NEGATIVE,
                        -15.5,
                        -10.3,
                        5.2,
                        3.1);

        assertEquals(-15.5, signal.roc10());
        assertEquals(-10.3, signal.roc20());
        assertEquals(5.2, signal.previousRoc10());
        assertEquals(3.1, signal.previousRoc20());
    }

    @Test
    void zeroRocValues_storedCorrectly() {
        MomentumRocSignal signal =
                new MomentumRocSignal(
                        "XLV",
                        "Health Care",
                        SignalType.MOMENTUM_TURNING_POSITIVE,
                        0.0,
                        0.0,
                        0.0,
                        0.0);

        assertEquals(0.0, signal.roc10());
        assertEquals(0.0, signal.roc20());
        assertEquals(0.0, signal.previousRoc10());
        assertEquals(0.0, signal.previousRoc20());
    }
}
