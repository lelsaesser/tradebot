package org.tradelite.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse.MarketHoliday;

@ExtendWith(MockitoExtension.class)
class MarketStatusServiceTest {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");

    @Mock private FinnhubClient finnhubClient;

    private MarketStatusService service;

    @BeforeEach
    void setUp() {
        service = new MarketStatusService(finnhubClient);
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
}
