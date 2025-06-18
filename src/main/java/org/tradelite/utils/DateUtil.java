package org.tradelite.utils;

import java.time.*;
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

    public static boolean isMarketOffHours(LocalTime currentTime) {
        if (currentTime == null) {
            ZoneId cetZone = ZoneId.of("Europe/Berlin"); // CET/CEST timezone
            ZonedDateTime nowInCET = ZonedDateTime.now(cetZone);
            currentTime = nowInCET.toLocalTime();
        }
        LocalTime start = LocalTime.of(22, 30);
        LocalTime end = LocalTime.of(14, 55);

        // Handles time ranges that go past midnight
        return currentTime.isAfter(start) || currentTime.isBefore(end);
    }
}
