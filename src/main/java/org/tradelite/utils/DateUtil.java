package org.tradelite.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    private DateUtil() {}

    public static String getDateTwoMonthsAgo(LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        LocalDate twoMonthsAgo = date.minusMonths(2);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return twoMonthsAgo.format(formatter);
    }
}
