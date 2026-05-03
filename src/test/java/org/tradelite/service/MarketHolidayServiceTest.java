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
class MarketHolidayServiceTest {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    @Mock private FinnhubClient finnhubClient;

    private MarketHolidayService service;

    @BeforeEach
    void setUp() {
        service = new MarketHolidayService(finnhubClient);
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
}
