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
     * Classifies the signal type based on how many available EMAs the price is below.
     *
     * @param emasBelow Number of available EMAs the current price is below
     * @param emasAvailable Total number of EMAs that could be calculated
     * @return The signal classification
     */
    public static EmaSignalType fromEmasBelow(int emasBelow, int emasAvailable) {
        if (emasAvailable == 0) {
            return YELLOW;
        }
        if (emasBelow <= 1) {
            return GREEN;
        } else if (emasBelow >= emasAvailable) {
            return RED;
        } else {
            return YELLOW;
        }
    }
}
