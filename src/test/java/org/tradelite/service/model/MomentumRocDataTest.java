package org.tradelite.service.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MomentumRocDataTest {

    @Test
    void defaultConstructor_createsUninitializedState() {
        MomentumRocData data = new MomentumRocData();

        assertEquals(0.0, data.getPreviousRoc10());
        assertEquals(0.0, data.getPreviousRoc20());
        assertFalse(data.isInitialized());
    }

    @Test
    void setters_updateValues() {
        MomentumRocData data = new MomentumRocData();

        data.setPreviousRoc10(10.5);
        data.setPreviousRoc20(-5.0);
        data.setInitialized(true);

        assertEquals(10.5, data.getPreviousRoc10());
        assertEquals(-5.0, data.getPreviousRoc20());
        assertTrue(data.isInitialized());
    }

    @Test
    void setters_allowNegativeValues() {
        MomentumRocData data = new MomentumRocData();

        data.setPreviousRoc10(-15.5);
        data.setPreviousRoc20(-25.0);

        assertEquals(-15.5, data.getPreviousRoc10());
        assertEquals(-25.0, data.getPreviousRoc20());
    }

    @Test
    void setters_allowZeroValues() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(5.0);
        data.setPreviousRoc20(10.0);

        data.setPreviousRoc10(0.0);
        data.setPreviousRoc20(0.0);

        assertEquals(0.0, data.getPreviousRoc10());
        assertEquals(0.0, data.getPreviousRoc20());
    }

    @Test
    void initialized_defaultsToFalse() {
        MomentumRocData data = new MomentumRocData();

        assertFalse(data.isInitialized());
    }

    @Test
    void initialized_canBeSetToTrue() {
        MomentumRocData data = new MomentumRocData();

        data.setInitialized(true);

        assertTrue(data.isInitialized());
    }

    @Test
    void initialized_canBeSetBackToFalse() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(1.0);
        data.setPreviousRoc20(2.0);
        data.setInitialized(true);

        data.setInitialized(false);

        assertFalse(data.isInitialized());
    }

    @Test
    void equals_sameValues_returnsTrue() {
        MomentumRocData data1 = new MomentumRocData();
        data1.setPreviousRoc10(5.0);
        data1.setPreviousRoc20(10.0);
        data1.setInitialized(true);

        MomentumRocData data2 = new MomentumRocData();
        data2.setPreviousRoc10(5.0);
        data2.setPreviousRoc20(10.0);
        data2.setInitialized(true);

        assertEquals(data1, data2);
    }

    @Test
    void equals_differentValues_returnsFalse() {
        MomentumRocData data1 = new MomentumRocData();
        data1.setPreviousRoc10(5.0);
        data1.setPreviousRoc20(10.0);
        data1.setInitialized(true);

        MomentumRocData data2 = new MomentumRocData();
        data2.setPreviousRoc10(-5.0);
        data2.setPreviousRoc20(10.0);
        data2.setInitialized(true);

        assertNotEquals(data1, data2);
    }

    @Test
    void hashCode_sameValues_returnsSameHashCode() {
        MomentumRocData data1 = new MomentumRocData();
        data1.setPreviousRoc10(5.0);
        data1.setPreviousRoc20(10.0);
        data1.setInitialized(true);

        MomentumRocData data2 = new MomentumRocData();
        data2.setPreviousRoc10(5.0);
        data2.setPreviousRoc20(10.0);
        data2.setInitialized(true);

        assertEquals(data1.hashCode(), data2.hashCode());
    }

    @Test
    void toString_containsFieldValues() {
        MomentumRocData data = new MomentumRocData();
        data.setPreviousRoc10(5.5);
        data.setPreviousRoc20(-2.3);
        data.setInitialized(true);

        String str = data.toString();

        assertTrue(str.contains("5.5"));
        assertTrue(str.contains("-2.3"));
        assertTrue(str.contains("true"));
    }
}
