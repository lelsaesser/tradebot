package org.tradelite.quant;

public record VfiAnalysis(
        String symbol, String displayName, double vfiValue, double signalLineValue) {

    public static final int MIN_DATA_POINTS = 136;

    public boolean isVfiPositive() {
        return vfiValue > 0 && signalLineValue > 0;
    }
}
