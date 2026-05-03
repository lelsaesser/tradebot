package org.tradelite.service;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse.MarketHoliday;
import org.tradelite.utils.DateUtil;

@Slf4j
@Service
public class MarketHolidayService {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);

    private final FinnhubClient finnhubClient;
    private final Map<LocalDate, MarketHoliday> holidayCache = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    public MarketHolidayService(FinnhubClient finnhubClient) {
        this.finnhubClient = finnhubClient;
    }

    @PostConstruct
    void loadHolidays() {
        fetchAndCacheHolidays();
    }

    @Scheduled(fixedDelay = 300_000)
    void retryIfNeeded() {
        if (!loaded) {
            log.info("Holiday cache is empty, retrying fetch...");
            fetchAndCacheHolidays();
        }
    }

    private void fetchAndCacheHolidays() {
        MarketHolidayResponse response = finnhubClient.getMarketHolidays();
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            log.warn("Failed to load market holidays from Finnhub — will retry in 5 minutes");
            return;
        }

        holidayCache.clear();
        for (MarketHoliday holiday : response.getData()) {
            LocalDate date = LocalDate.parse(holiday.getAtDate());
            holidayCache.put(date, holiday);
        }
        loaded = true;
        log.info("Loaded {} market holidays from Finnhub", holidayCache.size());
    }

    public boolean isMarketOpen(ZonedDateTime dateTime) {
        if (dateTime == null) {
            dateTime = ZonedDateTime.now(NY_ZONE);
        }
        ZonedDateTime nyTime = dateTime.withZoneSameInstant(NY_ZONE);

        if (!DateUtil.isWeekday(nyTime.getDayOfWeek())) {
            return false;
        }

        LocalDate today = nyTime.toLocalDate();
        LocalTime time = nyTime.toLocalTime();

        if (loaded) {
            MarketHoliday holiday = holidayCache.get(today);
            if (holiday != null) {
                String tradingHour = holiday.getTradingHour();
                if (tradingHour == null || tradingHour.isEmpty()) {
                    return false; // fully closed
                }
                // Early close: tradingHour = "09:30-13:00"
                LocalTime earlyClose = parseCloseTime(tradingHour);
                return !time.isBefore(MARKET_OPEN) && time.isBefore(earlyClose);
            }
        }

        // Normal hours (or fallback if cache not loaded)
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
    }

    private LocalTime parseCloseTime(String tradingHour) {
        // Format: "09:30-13:00"
        String[] parts = tradingHour.split("-");
        if (parts.length == 2) {
            return LocalTime.parse(parts[1]);
        }
        return MARKET_CLOSE;
    }

    boolean isLoaded() {
        return loaded;
    }
}
