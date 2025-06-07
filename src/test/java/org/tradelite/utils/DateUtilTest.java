package org.tradelite.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
}
