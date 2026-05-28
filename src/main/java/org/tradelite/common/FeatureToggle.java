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
    VFI_REPORT("vfiReport"),

    /** Controls whether real-time EMA pullback buy alerts are sent via Telegram */
    PULLBACK_BUY_ALERT("pullbackBuyAlert"),

    /** Controls whether the daily earnings calendar report is sent via Telegram */
    EARNINGS_CALENDAR_ALERT("earningsCalendarAlert"),

    /** Controls whether the daily accumulation detection alert is sent via Telegram */
    ACCUMULATION_DETECTION("accumulationDetection"),

    /** Controls whether Yahoo Finance intraday price fetching is active for international stocks */
    YAHOO_INTRADAY_PRICE_FETCH("yahooIntradayPriceFetch"),

    /**
     * Selects the HTTP transport for Yahoo Finance: ON uses {@code java.net.http.HttpClient}; OFF
     * uses ProcessBuilder + curl (legacy). Temporary toggle for #435; will be removed in the
     * cleanup follow-up (#457) once verified in production.
     */
    YAHOO_HTTP_CLIENT("yahooHttpClient");

    private final String key;
}
