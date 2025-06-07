package org.tradelite.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    private DateUtil() {}

    public static String getDateTwoMonthsAgo(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        LocalDate twoMonthsAgo = date.minusMonths(2).withDayOfMonth(date.getDayOfMonth());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return twoMonthsAgo.format(formatter);
    }

    public static boolean isWeekday(DayOfWeek dayOfWeek) {
        if (dayOfWeek == null) {
            dayOfWeek = LocalDate.now().getDayOfWeek();
        }
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }
}
