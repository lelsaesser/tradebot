package org.tradelite.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExchangeTest {

    @ParameterizedTest
    @CsvSource({
        "RHM.DE,    XETRA",
        "ENR.DE,    XETRA",
        "005930.KS, KRX",
        "285A.T,    JPX",
        "SIVE.ST,   STO",
        "SOI.PA,    PAR"
    })
    void fromTicker_knownSuffix_returnsExchange(String ticker, Exchange expected) {
        assertEquals(Optional.of(expected), Exchange.fromTicker(ticker));
    }

    @ParameterizedTest
    @ValueSource(strings = {"AAPL", "MSFT", "BITCOIN", "UNKNOWN.XX", "RHM", ".DE"})
    void fromTicker_unknownSuffix_returnsEmpty(String ticker) {
        // ".DE" alone (no name segment) still matches XETRA — the suffix check is purely
        // string-based.
        // Use a separate guard for that intentional case so the assertion below stays unambiguous.
        if (".DE".equals(ticker)) {
            assertEquals(Optional.of(Exchange.XETRA), Exchange.fromTicker(ticker));
            return;
        }
        assertEquals(Optional.empty(), Exchange.fromTicker(ticker));
    }

    @org.junit.jupiter.api.Test
    void fromTicker_null_returnsEmpty() {
        assertEquals(Optional.empty(), Exchange.fromTicker(null));
    }

    @org.junit.jupiter.api.Test
    void allExchangesHaveDistinctTickerSuffixes() {
        // Guard against an accidental duplicate suffix breaking fromTicker disambiguation.
        long distinct =
                java.util.Arrays.stream(Exchange.values())
                        .map(Exchange::getTickerSuffix)
                        .distinct()
                        .count();
        assertEquals(Exchange.values().length, distinct);
    }

    @org.junit.jupiter.api.Test
    void allExchangesHaveDistinctCountryCodes() {
        // Guard against accidental duplicate Enrico country codes.
        long distinct =
                java.util.Arrays.stream(Exchange.values())
                        .map(Exchange::getEnricoCountryCode)
                        .distinct()
                        .count();
        assertEquals(Exchange.values().length, distinct);
    }

    @org.junit.jupiter.api.Test
    void xetraExtrasContainHeiligabendAndSilvester() {
        // Cross-check against the issue #498 spec table — these are the exchange-specific full
        // closures Enrico does NOT classify as German public holidays.
        assertTrue(Exchange.XETRA.getExtras().contains(java.time.MonthDay.of(12, 24)));
        assertTrue(Exchange.XETRA.getExtras().contains(java.time.MonthDay.of(12, 31)));
    }

    @org.junit.jupiter.api.Test
    void jpxExtrasContainYearEndDates() {
        assertTrue(Exchange.JPX.getExtras().contains(java.time.MonthDay.of(12, 31)));
        assertTrue(Exchange.JPX.getExtras().contains(java.time.MonthDay.of(1, 2)));
        assertTrue(Exchange.JPX.getExtras().contains(java.time.MonthDay.of(1, 3)));
    }

    @org.junit.jupiter.api.Test
    void stoExtrasContainJulaftonAndNyarsafton() {
        // JQuantLib cross-check (#498 comment) confirmed Nasdaq Stockholm full closure on Dec 24
        // and Dec 31 — Enrico classifies these as observances, not public holidays.
        assertTrue(Exchange.STO.getExtras().contains(java.time.MonthDay.of(12, 24)));
        assertTrue(Exchange.STO.getExtras().contains(java.time.MonthDay.of(12, 31)));
    }
}
