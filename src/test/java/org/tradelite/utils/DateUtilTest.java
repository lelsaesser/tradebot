package org.tradelite.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

class DateUtilTest {

    @Test
    void getDateTwoMonthsAgo_givenDate() {
        LocalDate date = LocalDate.of(2020, 10, 7);

        String result = DateUtil.getDateTwoMonthsAgo(date);

        assertThat(result, is("2020-08-07"));
    }

    @Test
    void getDateTwoMonthsAgo_defaultDate() {
        String result = DateUtil.getDateTwoMonthsAgo(null);

        assertThat(result, is(LocalDate.now().minusMonths(2).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
    }

    @Test
    void isWeekday_defaultValue() {
        boolean isWeekday = DateUtil.isWeekday(null);
        LocalDate today = LocalDate.now();
        boolean expected = today.getDayOfWeek().getValue() < 6; // 1-5 are weekdays, 6-7 are weekend

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
            { true, DayOfWeek.MONDAY },
            { true, DayOfWeek.TUESDAY },
            { true, DayOfWeek.WEDNESDAY },
            { true, DayOfWeek.THURSDAY },
            { true, DayOfWeek.FRIDAY },
            { false, DayOfWeek.SATURDAY },
            { false, DayOfWeek.SUNDAY }
        };
    }

    @Test
    void returnsTrue_whenTimeIsBeforeEndOfMarketHours() {
        LocalTime time = LocalTime.of(7, 59); // 07:59
        boolean marketClosed = DateUtil.isMarketOffHours(time);
        assertThat(marketClosed, is(true));
    }

    @Test
    void returnsTrue_whenTimeIsAfterStartOfOffHours() {
        LocalTime time = LocalTime.of(23, 1); // 23:01
        boolean marketClosed = DateUtil.isMarketOffHours(time);
        assertThat(marketClosed, is(true));
    }

    @Test
    void returnsFalse_whenTimeIsDuringMarketHours() {
        LocalTime time = LocalTime.of(15, 0); // 15:00
        boolean marketClosed = DateUtil.isMarketOffHours(time);
        assertThat(marketClosed, is(false));
    }

    @Test
    void returnsFalse_whenTimeIsExactlyAtMarketOpen() {
        LocalTime time = LocalTime.of(14, 30); // 14:30
        boolean marketClosed = DateUtil.isMarketOffHours(time);
        assertThat(marketClosed, is(false));
    }

    @Test
    void returnsFalse_whenTimeIsExactlyAtOffHoursStart() {
        LocalTime time = LocalTime.of(23, 0); // 23:00
        boolean marketClosed = DateUtil.isMarketOffHours(time);
        assertThat(marketClosed, is(false));
    }

    @Test
    void returnsTrue_whenTimeIsExactlyAtOffHoursEnd() {
        LocalTime time = LocalTime.of(14, 29); // Just before market open
        boolean marketClosed = DateUtil.isMarketOffHours(time);
        assertThat(marketClosed, is(true));
    }

    @Test
    void returnsValue_whenCurrentTimeIsUsed() {
        boolean marketClosed = DateUtil.isMarketOffHours(null);

        LocalTime currentTime = LocalTime.now();
        LocalTime start = LocalTime.of(23, 0); // 23:00
        LocalTime end = LocalTime.of(14, 30);    // 14:30
        boolean expected = currentTime.isAfter(start) || currentTime.isBefore(end);
        assertThat(marketClosed, is(expected));
    }
}
