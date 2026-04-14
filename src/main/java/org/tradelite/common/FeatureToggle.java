package org.tradelite.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Centralized enum for all feature toggle names. This ensures consistency and provides a single
 * source of truth for feature toggle identifiers.
 *
 * <p>Toggle values correspond to keys in config/feature-toggles.json
 */
@Getter
@RequiredArgsConstructor
public enum FeatureToggle {
    /** Controls whether Finnhub price quotes are persisted to SQLite for historical data */
    FINNHUB_PRICE_COLLECTION("finnhubPriceCollection"),

    /** Controls whether the daily EMA report is sent via Telegram */
    EMA_REPORT("emaReport"),

    /** Controls whether the daily combined RS+VFI report is sent via Telegram */
    VFI_REPORT("vfiReport");

    private final String key;
}
