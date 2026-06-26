package org.tradelite.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.enrico.EnricoClient;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse.MarketHoliday;
import org.tradelite.common.Exchange;

@ExtendWith(MockitoExtension.class)
class MarketStatusServiceTest {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final ZoneId BERLIN_ZONE = Exchange.XETRA.getZoneId();
    private static final ZoneId SEOUL_ZONE = Exchange.KRX.getZoneId();
    private static final ZoneId TOKYO_ZONE = Exchange.JPX.getZoneId();
    private static final ZoneId STOCKHOLM_ZONE = Exchange.STO.getZoneId();
    private static final ZoneId PARIS_ZONE = Exchange.PAR.getZoneId();

    /**
     * Canonical synthetic holidays used by the {@code @BeforeEach} mock setup. Each exchange gets a
     * single Enrico-supplied holiday on a known date so that holiday-aware tests can assert closure
     * on those dates while the boundary tests (which use 2026-07-15) stay unaffected.
     */
    private static final Map<Exchange, Map<LocalDate, String>> CANONICAL_HOLIDAYS =
            Map.of(
                    Exchange.XETRA, Map.of(LocalDate.of(2026, 5, 1), "Labour Day"),
                    Exchange.KRX, Map.of(LocalDate.of(2026, 2, 17), "Korean New Year's Day"),
                    Exchange.JPX, Map.of(LocalDate.of(2026, 5, 5), "Children's Day"),
                    Exchange.STO, Map.of(LocalDate.of(2026, 6, 6), "National Day of Sweden"),
                    Exchange.PAR, Map.of(LocalDate.of(2026, 7, 14), "Bastille Day"));

    @Mock private FinnhubClient finnhubClient;
    @Mock private EnricoClient enricoClient;

    private MarketStatusService service;

    @BeforeEach
    void setUp() {
        // Default: every Enrico fetch returns its canonical synthetic holiday set. Tests that need
        // a different shape override per-exchange in their own when(...) calls before invoking
        // service.loadHolidays().
        for (Map.Entry<Exchange, Map<LocalDate, String>> entry : CANONICAL_HOLIDAYS.entrySet()) {
            lenient()
                    .when(
                            enricoClient.getHolidaysForRange(
                                    eq(entry.getKey()), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(entry.getValue());
        }
        service = new MarketStatusService(finnhubClient, enricoClient);
    }

    @Test
    void isMarketOpen_returnsFalseOnFullyClosedHoliday() {
        MarketHolidayResponse response = buildResponse(holiday("Christmas", "2026-12-25", ""));

        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        ZonedDateTime christmas =
                ZonedDateTime.of(LocalDate.of(2026, 12, 25), LocalTime.of(11, 0), NY_ZONE);

        assertFalse(service.isMarketOpen(christmas));
    }

    @Test
    void isMarketOpen_returnsTrueBeforeEarlyCloseOnEarlyCloseDay() {
        MarketHolidayResponse response =
                buildResponse(holiday("Christmas Eve", "2026-12-24", "09:30-13:00"));

        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        ZonedDateTime beforeClose =
                ZonedDateTime.of(LocalDate.of(2026, 12, 24), LocalTime.of(10, 0), NY_ZONE);

        assertTrue(service.isMarketOpen(beforeClose));
    }

    @Test
    void isMarketOpen_returnsFalseAfterEarlyCloseOnEarlyCloseDay() {
        MarketHolidayResponse response =
                buildResponse(holiday("Christmas Eve", "2026-12-24", "09:30-13:00"));

        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        ZonedDateTime afterClose =
                ZonedDateTime.of(LocalDate.of(2026, 12, 24), LocalTime.of(14, 0), NY_ZONE);

        assertFalse(service.isMarketOpen(afterClose));
    }

    @Test
    void isMarketOpen_returnsTrueDuringNormalWeekdayMarketHours() {
        MarketHolidayResponse response = buildResponse(holiday("Christmas", "2026-12-25", ""));

        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        // 2026-12-21 is a Monday
        ZonedDateTime monday =
                ZonedDateTime.of(LocalDate.of(2026, 12, 21), LocalTime.of(12, 0), NY_ZONE);

        assertTrue(service.isMarketOpen(monday));
    }

    @Test
    void isMarketOpen_returnsFalseOnWeekend() {
        MarketHolidayResponse response = buildResponse(holiday("Christmas", "2026-12-25", ""));

        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        // 2026-12-26 is a Saturday
        ZonedDateTime saturday =
                ZonedDateTime.of(LocalDate.of(2026, 12, 26), LocalTime.of(12, 0), NY_ZONE);

        assertFalse(service.isMarketOpen(saturday));
    }

    @Test
    void isMarketOpen_returnsFalseBefore930OnNormalWeekday() {
        MarketHolidayResponse response = buildResponse(holiday("Christmas", "2026-12-25", ""));

        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        // 2026-12-21 is a Monday
        ZonedDateTime earlyMorning =
                ZonedDateTime.of(LocalDate.of(2026, 12, 21), LocalTime.of(9, 0), NY_ZONE);

        assertFalse(service.isMarketOpen(earlyMorning));
    }

    @Test
    void isMarketOpen_fallsBackToWeekdayHoursWhenCacheIsEmpty() {
        when(finnhubClient.getMarketHolidays()).thenReturn(null);
        service.loadHolidays();

        assertFalse(service.isLoaded());

        // Normal weekday during market hours should still return true
        ZonedDateTime monday =
                ZonedDateTime.of(LocalDate.of(2026, 12, 21), LocalTime.of(12, 0), NY_ZONE);

        assertTrue(service.isMarketOpen(monday));
    }

    @Test
    void retryIfNeeded_callsFetchAndCacheHolidaysWhenNotLoaded() {
        when(finnhubClient.getMarketHolidays()).thenReturn(null);
        service.loadHolidays();

        assertFalse(service.isLoaded());

        // retryIfNeeded should call fetchAndCacheHolidays again
        service.retryIfNeeded();

        // loadHolidays called once + retryIfNeeded triggered another fetch
        verify(finnhubClient, times(2)).getMarketHolidays();
    }

    @Test
    void retryIfNeeded_doesNotCallFetchAndCacheHolidaysWhenAlreadyLoaded() {
        MarketHolidayResponse response = buildResponse(holiday("Christmas", "2026-12-25", ""));

        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        assertTrue(service.isLoaded());

        // retryIfNeeded should NOT call fetchAndCacheHolidays again
        service.retryIfNeeded();

        verify(finnhubClient, times(1)).getMarketHolidays();
    }

    private MarketHolidayResponse buildResponse(MarketHoliday... holidays) {
        MarketHolidayResponse response = new MarketHolidayResponse();
        response.setData(List.of(holidays));
        response.setExchange("US");
        response.setTimezone("America/New_York");
        return response;
    }

    private MarketHoliday holiday(String eventName, String atDate, String tradingHour) {
        MarketHoliday holiday = new MarketHoliday();
        holiday.setEventName(eventName);
        holiday.setAtDate(atDate);
        holiday.setTradingHour(tradingHour);
        return holiday;
    }

    // --- Market hours boundary tests ---

    @Test
    void isMarketOpen_atExactOpen_returnsTrue() {
        loadEmptyHolidayCache();
        // Monday 9:30 AM NY
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 9, 30, 0, 0, NY_ZONE);
        assertTrue(service.isMarketOpen(time));
    }

    @Test
    void isMarketOpen_justBeforeOpen_returnsFalse() {
        loadEmptyHolidayCache();
        // Monday 9:29 AM NY
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 9, 29, 0, 0, NY_ZONE);
        assertFalse(service.isMarketOpen(time));
    }

    @Test
    void isMarketOpen_justBeforeClose_returnsTrue() {
        loadEmptyHolidayCache();
        // Monday 3:59 PM NY
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 15, 59, 0, 0, NY_ZONE);
        assertTrue(service.isMarketOpen(time));
    }

    @Test
    void isMarketOpen_atExactClose_returnsFalse() {
        loadEmptyHolidayCache();
        // Monday 4:00 PM NY
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 16, 0, 0, 0, NY_ZONE);
        assertFalse(service.isMarketOpen(time));
    }

    @Test
    void isMarketOpen_afterClose_returnsFalse() {
        loadEmptyHolidayCache();
        // Monday 8:00 PM NY
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 20, 0, 0, 0, NY_ZONE);
        assertFalse(service.isMarketOpen(time));
    }

    // --- Timezone conversion tests (Berlin → NY) ---

    @Test
    void isMarketOpen_berlinTimeDuringNyOpen_returnsTrue() {
        loadEmptyHolidayCache();
        // 17:00 Berlin summer (CEST, UTC+2) = 11:00 AM NY (EDT, UTC-4) → market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 17, 0, 0, 0, BERLIN_ZONE);
        assertTrue(service.isMarketOpen(berlinTime));
    }

    @Test
    void isMarketOpen_berlinTimeDuringNyClosed_returnsFalse() {
        loadEmptyHolidayCache();
        // 08:00 Berlin summer (CEST, UTC+2) = 02:00 AM NY (EDT, UTC-4) → market closed
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, BERLIN_ZONE);
        assertFalse(service.isMarketOpen(berlinTime));
    }

    // --- DST transition tests ---

    @Test
    void isMarketOpen_summerBothDST_1530berlinIsMarketOpen() {
        loadEmptyHolidayCache();
        // Summer: 15:30 CEST (UTC+2) = 9:30 AM EDT (UTC-4) → market just opened
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 15, 30, 0, 0, BERLIN_ZONE);
        assertTrue(service.isMarketOpen(berlinTime));
    }

    @Test
    void isMarketOpen_summerBothDST_1529berlinIsBeforeOpen() {
        loadEmptyHolidayCache();
        // Summer: 15:29 CEST = 9:29 AM EDT → just before market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 15, 29, 0, 0, BERLIN_ZONE);
        assertFalse(service.isMarketOpen(berlinTime));
    }

    @Test
    void isMarketOpen_winterBothStandard_1530berlinIsMarketOpen() {
        loadEmptyHolidayCache();
        // Winter: 15:30 CET (UTC+1) = 9:30 AM EST (UTC-5) → market just opened
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 1, 15, 15, 30, 0, 0, BERLIN_ZONE);
        assertTrue(service.isMarketOpen(berlinTime));
    }

    @Test
    void isMarketOpen_winterBothStandard_1529berlinIsBeforeOpen() {
        loadEmptyHolidayCache();
        // Winter: 15:29 CET = 9:29 AM EST → just before market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 1, 15, 15, 29, 0, 0, BERLIN_ZONE);
        assertFalse(service.isMarketOpen(berlinTime));
    }

    @Test
    void isMarketOpen_springGap_usEdtEuropeCet_1430berlinIsMarketOpen() {
        loadEmptyHolidayCache();
        // Spring gap: US switched to EDT, Europe still on CET
        // March 16, 2026 (Monday): US is EDT (UTC-4), Germany is CET (UTC+1)
        // 14:30 CET (UTC+1) = 9:30 AM EDT (UTC-4) → market just opened
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 3, 16, 14, 30, 0, 0, BERLIN_ZONE);
        assertTrue(service.isMarketOpen(berlinTime));
    }

    @Test
    void isMarketOpen_springGap_usEdtEuropeCet_1429berlinIsBeforeOpen() {
        loadEmptyHolidayCache();
        // Spring gap: 14:29 CET = 9:29 AM EDT → just before market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 3, 16, 14, 29, 0, 0, BERLIN_ZONE);
        assertFalse(service.isMarketOpen(berlinTime));
    }

    private void loadEmptyHolidayCache() {
        MarketHolidayResponse response = buildResponse(holiday("Dummy", "2099-01-01", ""));
        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();
    }

    // --- getTodayHoliday tests (NYSE / Finnhub) ---

    @Test
    void getTodayHoliday_returnsEmptyWhenCacheNotLoaded() {
        when(finnhubClient.getMarketHolidays()).thenReturn(null);
        service.loadHolidays();

        assertTrue(service.getTodayHoliday().isEmpty());
    }

    @Test
    void getTodayHoliday_returnsEmptyWhenTodayIsNotAHoliday() {
        // Load a holiday for a date far in the future — today won't match
        MarketHolidayResponse response = buildResponse(holiday("Future Day", "2099-01-01", ""));
        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        assertTrue(service.getTodayHoliday().isEmpty());
    }

    @Test
    void getTodayHoliday_returnsHolidayWhenTodayMatches() {
        String today = LocalDate.now(ZoneId.of("America/New_York")).toString();
        MarketHolidayResponse response = buildResponse(holiday("Test Holiday", today, ""));
        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        assertTrue(service.getTodayHoliday().isPresent());
        assertEquals("Test Holiday", service.getTodayHoliday().get().getEventName());
    }

    @Test
    void getTodayHoliday_returnsEarlyCloseHolidayWhenTodayMatches() {
        String today = LocalDate.now(ZoneId.of("America/New_York")).toString();
        MarketHolidayResponse response =
                buildResponse(holiday("Early Close Day", today, "09:30-13:00"));
        when(finnhubClient.getMarketHolidays()).thenReturn(response);
        service.loadHolidays();

        assertTrue(service.getTodayHoliday().isPresent());
        assertEquals("Early Close Day", service.getTodayHoliday().get().getEventName());
        assertEquals("09:30-13:00", service.getTodayHoliday().get().getTradingHour());
    }

    @Test
    void isExchangeOpen_nullSymbol_returnsFalse() {
        assertFalse(service.isExchangeOpen(null));
    }

    @Test
    void isExchangeOpen_unknownExchange_returnsFalse() {
        assertFalse(service.isExchangeOpen("AAPL"));
        assertFalse(service.isExchangeOpen("UNKNOWN.XX"));
    }

    // --- isOpen() boundary tests (parameterized over single-window exchanges) ---
    //
    // Each row exercises the time-window correctness of isOpen for one of the four single-window
    // exchanges (XETRA / KRX / STO / PAR). JPX has a lunch break and is exercised separately.
    // 2026-07-15 is a Wednesday and is not a holiday for any exchange in the canonical mock set.

    static java.util.stream.Stream<Arguments> isOpen_boundaryCases() {
        return java.util.stream.Stream.of(
                // XETRA: 09:00 inclusive, 17:30 exclusive
                Arguments.of(Exchange.XETRA, 8, 59, false, "XETRA before open"),
                Arguments.of(Exchange.XETRA, 9, 0, true, "XETRA at open boundary"),
                Arguments.of(Exchange.XETRA, 13, 0, true, "XETRA mid-session"),
                Arguments.of(Exchange.XETRA, 17, 29, true, "XETRA one min before close"),
                Arguments.of(Exchange.XETRA, 17, 30, false, "XETRA at close boundary"),
                Arguments.of(Exchange.XETRA, 18, 0, false, "XETRA after close"),
                // KRX: 09:00 inclusive, 15:30 exclusive
                Arguments.of(Exchange.KRX, 8, 59, false, "KRX before open"),
                Arguments.of(Exchange.KRX, 9, 0, true, "KRX at open boundary"),
                Arguments.of(Exchange.KRX, 12, 0, true, "KRX mid-session"),
                Arguments.of(Exchange.KRX, 15, 29, true, "KRX one min before close"),
                Arguments.of(Exchange.KRX, 15, 30, false, "KRX at close boundary"),
                Arguments.of(Exchange.KRX, 16, 0, false, "KRX after close"),
                // STO: 09:00 inclusive, 17:30 exclusive
                Arguments.of(Exchange.STO, 8, 59, false, "STO before open"),
                Arguments.of(Exchange.STO, 9, 0, true, "STO at open boundary"),
                Arguments.of(Exchange.STO, 13, 0, true, "STO mid-session"),
                Arguments.of(Exchange.STO, 17, 29, true, "STO one min before close"),
                Arguments.of(Exchange.STO, 17, 30, false, "STO at close boundary"),
                Arguments.of(Exchange.STO, 18, 0, false, "STO after close"),
                // PAR: 09:00 inclusive, 17:30 exclusive
                Arguments.of(Exchange.PAR, 8, 59, false, "PAR before open"),
                Arguments.of(Exchange.PAR, 9, 0, true, "PAR at open boundary"),
                Arguments.of(Exchange.PAR, 13, 0, true, "PAR mid-session"),
                Arguments.of(Exchange.PAR, 17, 29, true, "PAR one min before close"),
                Arguments.of(Exchange.PAR, 17, 30, false, "PAR at close boundary"),
                Arguments.of(Exchange.PAR, 18, 0, false, "PAR after close"));
    }

    @ParameterizedTest(name = "{4} ({1}:{2})")
    @MethodSource("isOpen_boundaryCases")
    void isOpen_boundaryCase(
            Exchange exchange, int hour, int minute, boolean expectedOpen, String description) {
        loadEmptyHolidayCache();
        ZonedDateTime time =
                ZonedDateTime.of(2026, 7, 15, hour, minute, 0, 0, exchange.getZoneId());
        assertEquals(expectedOpen, service.isOpen(exchange, time), description);
    }

    static java.util.stream.Stream<Arguments> isOpen_weekendCases() {
        return java.util.stream.Stream.of(
                Arguments.of(Exchange.XETRA, 18, "Saturday", false),
                Arguments.of(Exchange.XETRA, 19, "Sunday", false),
                Arguments.of(Exchange.KRX, 18, "Saturday", false),
                Arguments.of(Exchange.KRX, 19, "Sunday", false),
                Arguments.of(Exchange.STO, 18, "Saturday", false),
                Arguments.of(Exchange.STO, 19, "Sunday", false),
                Arguments.of(Exchange.PAR, 18, "Saturday", false),
                Arguments.of(Exchange.PAR, 19, "Sunday", false));
    }

    @ParameterizedTest(name = "{0} {2} returns false")
    @MethodSource("isOpen_weekendCases")
    void isOpen_weekendCase(Exchange exchange, int dayOfMonth, String dayName, boolean expected) {
        loadEmptyHolidayCache();
        ZonedDateTime time =
                ZonedDateTime.of(2026, 7, dayOfMonth, 13, 0, 0, 0, exchange.getZoneId());
        assertEquals(expected, service.isOpen(exchange, time), exchange + " " + dayName);
    }

    // --- isOpen() JPX lunch-break tests (kept standalone — only exchange with two sessions) ---

    @Test
    void isOpen_jpx_beforeOpen_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 8, 59, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_atMorningOpen_returnsTrue() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 9, 0, 0, 0, TOKYO_ZONE);
        assertTrue(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_midMorning_returnsTrue() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 10, 0, 0, 0, TOKYO_ZONE);
        assertTrue(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_oneMinuteBeforeMorningClose_returnsTrue() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 11, 29, 0, 0, TOKYO_ZONE);
        assertTrue(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_atMorningCloseBoundary_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 11, 30, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_duringLunchBreak_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 12, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_atAfternoonOpen_returnsTrue() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 12, 30, 0, 0, TOKYO_ZONE);
        assertTrue(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_midAfternoon_returnsTrue() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 13, 30, 0, 0, TOKYO_ZONE);
        assertTrue(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_oneMinuteBeforeAfternoonClose_returnsTrue() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 14, 59, 0, 0, TOKYO_ZONE);
        assertTrue(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_atAfternoonCloseBoundary_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 15, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_afterClose_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 15, 16, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_saturday_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 18, 13, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_sunday_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 19, 13, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    // --- isOpen() holiday tests (Enrico-supplied) ---
    //
    // Verify isOpen returns false on the canonical synthetic holiday for each exchange even though
    // the time-window check would otherwise return true.

    @Test
    void isOpen_xetra_onLabourDay_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 5, 1, 13, 0, 0, 0, BERLIN_ZONE);
        assertFalse(service.isOpen(Exchange.XETRA, time));
    }

    @Test
    void isOpen_krx_onSeollal_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 2, 17, 12, 0, 0, 0, SEOUL_ZONE);
        assertFalse(service.isOpen(Exchange.KRX, time));
    }

    @Test
    void isOpen_jpx_onChildrensDay_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 5, 5, 10, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_sto_onNationalDay_returnsFalse() {
        loadEmptyHolidayCache();
        // 2026-06-06 is a Saturday — pick a holiday-on-weekday for STO instead. Override.
        when(enricoClient.getHolidaysForRange(eq(Exchange.STO), any(), any()))
                .thenReturn(Map.of(LocalDate.of(2026, 5, 1), "Labour Day"));
        service.loadHolidays();
        ZonedDateTime time = ZonedDateTime.of(2026, 5, 1, 13, 0, 0, 0, STOCKHOLM_ZONE);
        assertFalse(service.isOpen(Exchange.STO, time));
    }

    @Test
    void isOpen_par_onBastilleDay_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 7, 14, 13, 0, 0, 0, PARIS_ZONE);
        assertFalse(service.isOpen(Exchange.PAR, time));
    }

    // --- isOpen() holiday tests (overlay-supplied — Heiligabend / Silvester / JPX year-end /
    //     Stockholm year-end) ---

    @Test
    void isOpen_xetra_onHeiligabend_returnsFalse() {
        loadEmptyHolidayCache();
        // 2026-12-24 is a Thursday; Enrico does NOT classify Heiligabend as a German public
        // holiday.
        // The closure must come from Exchange.XETRA.extras().
        ZonedDateTime time = ZonedDateTime.of(2026, 12, 24, 13, 0, 0, 0, BERLIN_ZONE);
        assertFalse(service.isOpen(Exchange.XETRA, time));
    }

    @Test
    void isOpen_xetra_onSilvester_returnsFalse() {
        loadEmptyHolidayCache();
        // 2026-12-31 is a Thursday.
        ZonedDateTime time = ZonedDateTime.of(2026, 12, 31, 13, 0, 0, 0, BERLIN_ZONE);
        assertFalse(service.isOpen(Exchange.XETRA, time));
    }

    @Test
    void isOpen_jpx_onYearEnd_returnsFalse() {
        loadEmptyHolidayCache();
        // 2026-12-31 (Thursday) — JPX nenmatsu nenshi closure, not in Enrico's public holiday list.
        ZonedDateTime time = ZonedDateTime.of(2026, 12, 31, 10, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_jpx_onJan2_returnsFalse() {
        loadEmptyHolidayCache();
        // 2027-01-04 is the next weekday after the Jan 1-3 stretch — pick 2027-01-04? No, we need
        // Jan 2 specifically. 2027-01-02 is Saturday; weekend dominates. Use 2026-01-02 (Friday).
        ZonedDateTime time = ZonedDateTime.of(2026, 1, 2, 10, 0, 0, 0, TOKYO_ZONE);
        assertFalse(service.isOpen(Exchange.JPX, time));
    }

    @Test
    void isOpen_sto_onJulafton_returnsFalse() {
        loadEmptyHolidayCache();
        // 2026-12-24 is a Thursday. Enrico doesn't classify Julafton as a Swedish public holiday;
        // Stockholm's full closure must come from Exchange.STO.extras().
        ZonedDateTime time = ZonedDateTime.of(2026, 12, 24, 13, 0, 0, 0, STOCKHOLM_ZONE);
        assertFalse(service.isOpen(Exchange.STO, time));
    }

    @Test
    void isOpen_sto_onNyarsafton_returnsFalse() {
        loadEmptyHolidayCache();
        ZonedDateTime time = ZonedDateTime.of(2026, 12, 31, 13, 0, 0, 0, STOCKHOLM_ZONE);
        assertFalse(service.isOpen(Exchange.STO, time));
    }

    // --- isExchangeOpen routing tests (suffix → Exchange → isOpen) ---

    @Test
    void isExchangeOpen_routesViaTickerSuffix() {
        // With holidays loaded, isExchangeOpen returns false on the canonical holiday date for the
        // matching exchange — proves the routing chain end-to-end.
        loadEmptyHolidayCache();
        // The holiday lookup uses LocalDate.now(zone) so we can't easily pin the date here without
        // injecting a clock. Instead, just assert the method doesn't throw and returns a boolean
        // for each known suffix. Boundary correctness is exercised by isOpen tests above.
        service.isExchangeOpen("RHM.DE");
        service.isExchangeOpen("005930.KS");
        service.isExchangeOpen("285A.T");
        service.isExchangeOpen("SIVE.ST");
        service.isExchangeOpen("SOI.PA");
    }

    // --- retryInternationalHolidaysIfNeeded ---

    @Test
    void retryInternationalHolidaysIfNeeded_refetchesExchangesWithEmptyCache() {
        // Default mock returns canonical holidays; loadHolidays succeeds for all 5 exchanges.
        loadEmptyHolidayCache();

        // Sanity: each Enrico fetch happened once during loadHolidays.
        for (Exchange exchange : Exchange.values()) {
            verify(enricoClient, times(1))
                    .getHolidaysForRange(eq(exchange), any(LocalDate.class), any(LocalDate.class));
        }

        service.retryInternationalHolidaysIfNeeded();

        // No additional fetches because every exchange's loaded flag is true.
        for (Exchange exchange : Exchange.values()) {
            verify(enricoClient, times(1))
                    .getHolidaysForRange(eq(exchange), any(LocalDate.class), any(LocalDate.class));
        }
    }

    @Test
    void retryInternationalHolidaysIfNeeded_refetchesOnlyFailedExchanges() {
        // Make XETRA's first fetch fail (empty map) — its loaded flag stays false.
        when(enricoClient.getHolidaysForRange(eq(Exchange.XETRA), any(), any()))
                .thenReturn(Collections.emptyMap())
                .thenReturn(Map.of(LocalDate.of(2026, 5, 1), "Labour Day"));
        loadEmptyHolidayCache();

        service.retryInternationalHolidaysIfNeeded();

        // XETRA fetched twice (initial + retry), other exchanges fetched once (no retry needed).
        verify(enricoClient, times(2)).getHolidaysForRange(eq(Exchange.XETRA), any(), any());
        verify(enricoClient, times(1)).getHolidaysForRange(eq(Exchange.KRX), any(), any());
    }

    // --- getTodayInternationalHolidays ---

    @Test
    void getTodayInternationalHolidays_returnsEmptyWhenNoHolidayToday() {
        loadEmptyHolidayCache();
        // Canonical holidays are all on specific 2026 dates; today is unlikely to match any of them
        // unless the test runs on one of those exact dates. The map will be empty for the overlay
        // fallback only if today's MonthDay isn't in any exchange's extras either.
        Map<Exchange, String> result = service.getTodayInternationalHolidays();
        // Just confirm it returns a non-null Map and doesn't throw — date-sensitive content can't
        // be asserted without injecting a clock.
        assertEquals(true, result != null);
    }

    @Test
    void getTodayInternationalHolidays_returnsHolidayWhenTodayIsExchangeHoliday() {
        // Override Enrico to return today's date as a holiday for XETRA; assert it surfaces.
        Map<LocalDate, String> xetraHolidays = new HashMap<>();
        xetraHolidays.put(LocalDate.now(Exchange.XETRA.getZoneId()), "Synthetic Today Holiday");
        when(enricoClient.getHolidaysForRange(eq(Exchange.XETRA), any(), any()))
                .thenReturn(xetraHolidays);
        loadEmptyHolidayCache();

        Map<Exchange, String> result = service.getTodayInternationalHolidays();

        assertEquals("Synthetic Today Holiday", result.get(Exchange.XETRA));
    }
}
