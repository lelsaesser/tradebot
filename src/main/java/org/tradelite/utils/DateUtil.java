package org.tradelite.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    public static String getDateTwoMonthsAgo() {
        LocalDate twoMonthsAgo = LocalDate.now().minusMonths(2).withDayOfMonth(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        return twoMonthsAgo.format(formatter);
    }
}
