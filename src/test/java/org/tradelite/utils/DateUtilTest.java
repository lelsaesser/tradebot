package org.tradelite.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.Test;

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

        assertThat(
                result,
                is(
                        LocalDate.now()
                                .minusMonths(2)
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))));
    }
}
