package org.tradelite.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.isA;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.*;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DateUtilTest {

    private static final ZoneId NY_ZONE = DateUtil.NY_ZONE;
    private static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");

    @Test
    void getDateTwoMonthsAgo_givenDate() {
        LocalDate date = LocalDate.of(2020, 10, 7);

        String result = DateUtil.getDateTwoMonthsAgo(date);

        assertThat(result, is("2020-08-07"));
    }

    @Test
    void getDateTwoMonthsAgo_defaultDate() {
        String result = DateUtil.getDateTwoMonthsAgo(null);

        assertThat(
                result,
                is(
                        LocalDate.now()
                                .minusMonths(2)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
    }

    @Test
    void isWeekday_defaultValue() {
        boolean isWeekday = DateUtil.isWeekday(null);
        LocalDate today = LocalDate.now();
        boolean expected = today.getDayOfWeek().getValue() < 6;

        assertThat(isWeekday, is(expected));
    }

    @ParameterizedTest
    @MethodSource("dayOfWeekProvider")
    void isWeekday_givenDayOfWeek(boolean isWeekday, DayOfWeek dayOfWeek) {
        boolean result = DateUtil.isWeekday(dayOfWeek);
        assertThat(result, is(isWeekday));
    }

    private static Object[][] dayOfWeekProvider() {
        return new Object[][] {
            {true, DayOfWeek.MONDAY},
            {true, DayOfWeek.TUESDAY},
            {true, DayOfWeek.WEDNESDAY},
            {true, DayOfWeek.THURSDAY},
            {true, DayOfWeek.FRIDAY},
            {false, DayOfWeek.SATURDAY},
            {false, DayOfWeek.SUNDAY}
        };
    }

    // --- isMarketOffHours tests using NY timezone ---

    @Test
    void isMarketOffHours_beforeOpen_returnsTrue() {
        // 7:00 AM NY time on a Monday
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 7, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isMarketOffHours(time), is(true));
    }

    @Test
    void isMarketOffHours_justBeforeOpen_returnsTrue() {
        // 9:29 AM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 9, 29, 0, 0, NY_ZONE);
        assertThat(DateUtil.isMarketOffHours(time), is(true));
    }

    @Test
    void isMarketOffHours_atOpen_returnsFalse() {
        // 9:30 AM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 9, 30, 0, 0, NY_ZONE);
        assertThat(DateUtil.isMarketOffHours(time), is(false));
    }

    @Test
    void isMarketOffHours_duringMarketHours_returnsFalse() {
        // 12:00 PM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 12, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isMarketOffHours(time), is(false));
    }

    @Test
    void isMarketOffHours_justBeforeClose_returnsFalse() {
        // 3:59 PM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 15, 59, 0, 0, NY_ZONE);
        assertThat(DateUtil.isMarketOffHours(time), is(false));
    }

    @Test
    void isMarketOffHours_atClose_returnsTrue() {
        // 4:00 PM NY time (market closes at 4:00, so 4:00 is off-hours)
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 16, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isMarketOffHours(time), is(true));
    }

    @Test
    void isMarketOffHours_afterClose_returnsTrue() {
        // 8:00 PM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 20, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isMarketOffHours(time), is(true));
    }

    @Test
    void isMarketOffHours_null_usesCurrentTime() {
        // Should not throw, returns a boolean
        boolean result = DateUtil.isMarketOffHours(null);
        assertThat(result, isA(Boolean.class));
    }

    // --- isMarketOffHours with Berlin timezone (auto-converts to NY) ---

    @Test
    void isMarketOffHours_berlinTimeDuringNyOpen_returnsFalse() {
        // 17:00 Berlin time in summer (CEST, UTC+2) = 11:00 AM NY (EDT, UTC-4) → market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 17, 0, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(false));
    }

    @Test
    void isMarketOffHours_berlinTimeDuringNyClosed_returnsTrue() {
        // 08:00 Berlin time in summer (CEST, UTC+2) = 02:00 AM NY (EDT, UTC-4) → market closed
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 8, 0, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(true));
    }

    // --- DST transition tests ---

    @Test
    void isMarketOffHours_summerTime_bothDST_1530berlinIsMarketOpen() {
        // Summer: Both US (EDT) and Germany (CEST) on DST
        // 15:30 CEST (UTC+2) = 13:30 UTC = 9:30 AM EDT (UTC-4) → market just opened
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 15, 30, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(false));
    }

    @Test
    void isMarketOffHours_summerTime_bothDST_1529berlinIsBeforeOpen() {
        // Summer: 15:29 CEST = 9:29 AM EDT → just before market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 15, 29, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(true));
    }

    @Test
    void isMarketOffHours_winterTime_bothStandard_1530berlinIsMarketOpen() {
        // Winter: Both US (EST) and Germany (CET) on standard time
        // 15:30 CET (UTC+1) = 14:30 UTC = 9:30 AM EST (UTC-5) → market just opened
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 1, 15, 15, 30, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(false));
    }

    @Test
    void isMarketOffHours_winterTime_bothStandard_1529berlinIsBeforeOpen() {
        // Winter: 15:29 CET = 9:29 AM EST → just before market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 1, 15, 15, 29, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(true));
    }

    @Test
    void isMarketOffHours_springGap_usEdtEuropeCet_1430berlinIsMarketOpen() {
        // Spring gap: US switched to EDT (second Sunday of March), Europe still on CET
        // (last Sunday of March). In 2026: US DST starts March 8, Europe March 29.
        // Pick March 15, 2026: US is EDT (UTC-4), Germany is CET (UTC+1)
        // 14:30 CET (UTC+1) = 13:30 UTC = 9:30 AM EDT (UTC-4) → market just opened
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 3, 15, 14, 30, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(false));
    }

    @Test
    void isMarketOffHours_springGap_usEdtEuropeCet_1429berlinIsBeforeOpen() {
        // Spring gap: 14:29 CET = 9:29 AM EDT → just before market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 3, 15, 14, 29, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(true));
    }

    @Test
    void isMarketOffHours_fallGap_usEstEuropeCest_1630berlinIsMarketOpen() {
        // Fall gap: Europe still on CEST, US already switched back to EST
        // In 2026: Europe ends CEST October 25, US ends EDT November 1.
        // Pick October 28, 2026: US is EST (UTC-5), Germany is CET (UTC+1)
        // Actually Oct 28 Germany is already CET. Let me pick Oct 26 instead.
        // Oct 25 2026 is Sunday (CEST ends), so Oct 26 is CET.
        // US ends EDT Nov 1 2026 (Sunday), so Oct 26 US is still EDT (UTC-4).
        // 16:30 CET (UTC+1) = 15:30 UTC = 11:30 AM EDT (UTC-4) → market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 10, 26, 16, 30, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isMarketOffHours(berlinTime), is(false));
    }

    // --- isStockMarketOpen tests ---

    @Test
    void isStockMarketOpen_weekdayDuringMarketHours_returnsTrue() {
        // Monday 11:00 AM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 11, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isStockMarketOpen(time), is(true));
    }

    @Test
    void isStockMarketOpen_weekendDuringMarketHours_returnsFalse() {
        // Saturday 11:00 AM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 28, 11, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isStockMarketOpen(time), is(false));
    }

    @Test
    void isStockMarketOpen_weekdayBeforeOpen_returnsFalse() {
        // Monday 8:00 AM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 8, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isStockMarketOpen(time), is(false));
    }

    @Test
    void isStockMarketOpen_weekdayAfterClose_returnsFalse() {
        // Monday 5:00 PM NY time
        ZonedDateTime time = ZonedDateTime.of(2026, 3, 30, 17, 0, 0, 0, NY_ZONE);
        assertThat(DateUtil.isStockMarketOpen(time), is(false));
    }

    @Test
    void isStockMarketOpen_null_usesCurrentTime() {
        boolean result = DateUtil.isStockMarketOpen(null);
        assertThat(result, isA(Boolean.class));
    }

    @Test
    void isStockMarketOpen_berlinTimeConvertsCorrectly() {
        // 17:00 Berlin summer time (CEST) on a Wednesday = 11:00 AM EDT → market open
        ZonedDateTime berlinTime = ZonedDateTime.of(2026, 7, 15, 17, 0, 0, 0, BERLIN_ZONE);
        assertThat(DateUtil.isStockMarketOpen(berlinTime), is(true));
    }
}
