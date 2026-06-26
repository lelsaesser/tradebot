package org.tradelite.common;

import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;

/**
 * Stock exchange the bot supports for international price evaluation. Each value carries the data
 * needed to gate per-exchange behavior: time zone, ticker suffix (used by {@link #fromTicker}),
 * Enrico API country code, and a {@code Set<MonthDay>} overlay of exchange-specific full-day
 * closures that aren't national public holidays (e.g. Heiligabend / Silvester on XETRA, year-end
 * closures on JPX, Christmas Eve / New Year's Eve on Nasdaq Stockholm).
 *
 * <p>NYSE intentionally lives outside this enum — it has its own Finnhub-driven holiday calendar
 * with early-close-day support that the Enrico-based path can't replicate. See {@link
 * org.tradelite.service.MarketStatusService#isMarketOpen}.
 *
 * <p>Added in #498.
 */
@Getter
public enum Exchange {
    XETRA(
            ZoneId.of("Europe/Berlin"),
            ".DE",
            "deu",
            Set.of(MonthDay.of(12, 24), MonthDay.of(12, 31))),
    KRX(ZoneId.of("Asia/Seoul"), ".KS", "kor", Set.of()),
    JPX(
            ZoneId.of("Asia/Tokyo"),
            ".T",
            "jpn",
            Set.of(MonthDay.of(12, 31), MonthDay.of(1, 2), MonthDay.of(1, 3))),
    STO(
            ZoneId.of("Europe/Stockholm"),
            ".ST",
            "swe",
            Set.of(MonthDay.of(12, 24), MonthDay.of(12, 31))),
    PAR(ZoneId.of("Europe/Paris"), ".PA", "fra", Set.of());

    private final ZoneId zoneId;
    private final String tickerSuffix;
    private final String enricoCountryCode;
    private final Set<MonthDay> extras;

    Exchange(ZoneId zoneId, String tickerSuffix, String enricoCountryCode, Set<MonthDay> extras) {
        this.zoneId = zoneId;
        this.tickerSuffix = tickerSuffix;
        this.enricoCountryCode = enricoCountryCode;
        this.extras = extras;
    }

    /**
     * Resolve a ticker to its exchange via suffix match. Returns {@code Optional.empty()} for null
     * input or tickers without a recognized international suffix (US/domestic tickers, unknown
     * suffixes).
     */
    public static Optional<Exchange> fromTicker(String ticker) {
        if (ticker == null) {
            return Optional.empty();
        }
        for (Exchange e : values()) {
            if (ticker.endsWith(e.tickerSuffix)) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
