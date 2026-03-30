package org.tradelite.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);

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

    /**
     * Checks whether the US stock market (NYSE/NASDAQ) is currently in off-hours. Market hours are
     * defined as 9:30 AM - 4:00 PM Eastern Time (America/New_York). Java's ZoneId handles EST/EDT
     * transitions automatically, so this correctly accounts for DST changes in both the US and the
     * caller's timezone.
     *
     * @param dateTime the date/time to check, or null to use the current time
     * @return true if the market is in off-hours, false if during trading hours
     */
    public static boolean isMarketOffHours(ZonedDateTime dateTime) {
        if (dateTime == null) {
            dateTime = ZonedDateTime.now(NY_ZONE);
        }
        ZonedDateTime nyTime = dateTime.withZoneSameInstant(NY_ZONE);
        LocalTime time = nyTime.toLocalTime();
        return time.isBefore(MARKET_OPEN) || !time.isBefore(MARKET_CLOSE);
    }

    /**
     * Checks whether the US stock market is currently open (weekday + trading hours).
     *
     * @param dateTime the date/time to check, or null to use the current time
     * @return true if the market is open
     */
    public static boolean isStockMarketOpen(ZonedDateTime dateTime) {
        if (dateTime == null) {
            dateTime = ZonedDateTime.now(NY_ZONE);
        }
        ZonedDateTime nyTime = dateTime.withZoneSameInstant(NY_ZONE);
        return isWeekday(nyTime.getDayOfWeek()) && !isMarketOffHours(nyTime);
    }
}
