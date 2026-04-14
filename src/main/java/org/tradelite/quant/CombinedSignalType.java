package org.tradelite.quant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CombinedSignalType {
    GREEN("\uD83D\uDFE2", "RS\u2191 + VFI\u2191"),
    YELLOW("\uD83D\uDFE1", "Mixed"),
    RED("\uD83D\uDD34", "RS\u2193 + VFI\u2193");

    private final String emoji;
    private final String label;

    public static CombinedSignalType classify(boolean rsPositive, boolean vfiPositive) {
        if (rsPositive && vfiPositive) {
            return GREEN;
        } else if (!rsPositive && !vfiPositive) {
            return RED;
        }
        return YELLOW;
    }
}
