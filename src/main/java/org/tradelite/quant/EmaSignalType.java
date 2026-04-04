package org.tradelite.quant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Classification of a stock's position relative to its 5 EMAs (9, 21, 50, 100, 200 day).
 *
 * <ul>
 *   <li><b>GREEN</b>: Below 0 or 1 EMAs (healthy trend)
 *   <li><b>YELLOW</b>: Below 2-4 EMAs (weakening trend)
 *   <li><b>RED</b>: Below all 5 EMAs (bearish)
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum EmaSignalType {
    GREEN("🟢", "Above EMAs"),
    YELLOW("🟡", "Mixed"),
    RED("🔴", "Below All EMAs");

    private final String emoji;
    private final String label;

    /**
     * Classifies the signal type based on how many EMAs the price is below.
     *
     * @param emasBelow Number of EMAs the current price is below (0-5)
     * @return The signal classification
     */
    public static EmaSignalType fromEmasBelow(int emasBelow) {
        if (emasBelow <= 1) {
            return GREEN;
        } else if (emasBelow >= 5) {
            return RED;
        } else {
            return YELLOW;
        }
    }
}
