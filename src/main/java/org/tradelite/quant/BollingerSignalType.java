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
 *   <li><b>SQUEEZE</b>: Absolute bandwidth below threshold — bands tightly compressed, breakout
 *       expected
 *   <li><b>HISTORICAL_SQUEEZE</b>: Bandwidth at historically low percentile vs. recent history
 * </ul>
 */
@Getter
public enum BollingerSignalType {

    /** Price closed at or above the upper Bollinger Band (%B >= 1.0). */
    UPPER_BAND_TOUCH("⬆️", "Overextended"),

    /** Price closed at or below the lower Bollinger Band (%B <= 0.0). */
    LOWER_BAND_TOUCH("⬇️", "Underextended"),

    /** Bandwidth below absolute threshold (< 4%) — bands tightly compressed, breakout expected. */
    SQUEEZE("🔄", "Squeeze"),

    /** Bandwidth at historically low percentile vs. recent history — requires 40+ data points. */
    HISTORICAL_SQUEEZE("📊", "Historical Squeeze");

    private final String emoji;
    private final String label;

    BollingerSignalType(String emoji, String label) {
        this.emoji = emoji;
        this.label = label;
    }
}
