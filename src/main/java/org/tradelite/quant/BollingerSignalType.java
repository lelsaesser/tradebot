package org.tradelite.quant;

import lombok.Getter;

/**
 * Signal types detected by Bollinger Band analysis.
 *
 * <p>Each signal type represents a distinct market condition:
 *
 * <ul>
 *   <li><b>UPPER_BAND_TOUCH</b>: Price at or above upper band — statistically overextended
 *   <li><b>LOWER_BAND_TOUCH</b>: Price at or below lower band — statistically underextended
 *   <li><b>SQUEEZE</b>: Bandwidth at historically low levels — volatility compression, expect
 *       breakout
 * </ul>
 */
@Getter
public enum BollingerSignalType {

    /** Price closed at or above the upper Bollinger Band (%B >= 1.0). */
    UPPER_BAND_TOUCH("⬆️", "Overextended"),

    /** Price closed at or below the lower Bollinger Band (%B <= 0.0). */
    LOWER_BAND_TOUCH("⬇️", "Underextended"),

    /** Bandwidth compressed to historically low levels — breakout imminent. */
    SQUEEZE("🔄", "Squeeze");

    private final String emoji;
    private final String label;

    BollingerSignalType(String emoji, String label) {
        this.emoji = emoji;
        this.label = label;
    }
}
