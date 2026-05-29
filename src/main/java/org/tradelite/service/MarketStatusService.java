package org.tradelite.service;

import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse.MarketHoliday;

@Slf4j
@Service
public class MarketStatusService {

    static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
    static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");
    static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");
    static final ZoneId TOKYO_ZONE = ZoneId.of("Asia/Tokyo");
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime XETRA_OPEN = LocalTime.of(9, 0);
    private static final LocalTime XETRA_CLOSE = LocalTime.of(17, 30);
    private static final LocalTime KRX_OPEN = LocalTime.of(9, 0);
    private static final LocalTime KRX_CLOSE = LocalTime.of(15, 30);
    private static final LocalTime JPX_MORNING_OPEN = LocalTime.of(9, 0);
    private static final LocalTime JPX_MORNING_CLOSE = LocalTime.of(11, 30);
    private static final LocalTime JPX_AFTERNOON_OPEN = LocalTime.of(12, 30);
    private static final LocalTime JPX_AFTERNOON_CLOSE = LocalTime.of(15, 0);

    private final FinnhubClient finnhubClient;
    private final AtomicReference<Map<LocalDate, MarketHoliday>> holidayCache =
            new AtomicReference<>(Collections.emptyMap());
    private volatile boolean loaded = false;

    public MarketStatusService(FinnhubClient finnhubClient) {
        this.finnhubClient = finnhubClient;
    }

    @PostConstruct
    void loadHolidays() {
        fetchAndCacheHolidays();
    }

    @Scheduled(fixedDelay = 300_000)
    void retryIfNeeded() {
        if (!loaded) {
            log.warn("Holiday cache is empty, retrying fetch...");
            fetchAndCacheHolidays();
        }
    }

    private void fetchAndCacheHolidays() {
        MarketHolidayResponse response = finnhubClient.getMarketHolidays();
        if (response == null || response.getData() == null || response.getData().isEmpty()) {
            log.warn("Failed to load market holidays from Finnhub — will retry in 5 minutes");
            return;
        }

        Map<LocalDate, MarketHoliday> newCache = new HashMap<>();
        for (MarketHoliday holiday : response.getData()) {
            LocalDate date = LocalDate.parse(holiday.getAtDate());
            newCache.put(date, holiday);
        }
        holidayCache.set(Collections.unmodifiableMap(newCache));
        loaded = true;
        log.info("Loaded {} market holidays from Finnhub", newCache.size());
    }

    public boolean isMarketOpen(ZonedDateTime dateTime) {
        if (dateTime == null) {
            dateTime = ZonedDateTime.now(NY_ZONE);
        }
        ZonedDateTime nyTime = dateTime.withZoneSameInstant(NY_ZONE);

        if (!isWeekday(nyTime.getDayOfWeek())) {
            return false;
        }

        LocalDate today = nyTime.toLocalDate();
        LocalTime time = nyTime.toLocalTime();

        if (loaded) {
            MarketHoliday holiday = holidayCache.get().get(today);
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

        // Normal hours (or fallback if cache not loaded, no holiday match)
        return !time.isBefore(MARKET_OPEN) && time.isBefore(MARKET_CLOSE);
    }

    private static boolean isWeekday(DayOfWeek dayOfWeek) {
        return dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY;
    }

    private LocalTime parseCloseTime(String tradingHour) {
        // Format: "09:30-13:00"
        String[] parts = tradingHour.split("-");
        if (parts.length == 2) {
            return LocalTime.parse(parts[1]);
        }
        return MARKET_CLOSE;
    }

    public Optional<MarketHoliday> getTodayHoliday() {
        if (!loaded) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now(NY_ZONE);
        return Optional.ofNullable(holidayCache.get().get(today));
    }

    public boolean isExchangeOpen(String symbol) {
        if (symbol == null) {
            return false;
        }
        if (symbol.endsWith(".DE")) {
            return isXetraOpen(ZonedDateTime.now(BERLIN_ZONE));
        }
        if (symbol.endsWith(".KS")) {
            return isKrxOpen(ZonedDateTime.now(SEOUL_ZONE));
        }
        if (symbol.endsWith(".T")) {
            return isJpxOpen(ZonedDateTime.now(TOKYO_ZONE));
        }
        log.warn("No exchange mapping found for symbol: {} — skipping price evaluation", symbol);
        return false;
    }

    boolean isXetraOpen(ZonedDateTime now) {
        if (!isWeekday(now.getDayOfWeek())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(XETRA_OPEN) && time.isBefore(XETRA_CLOSE);
    }

    boolean isKrxOpen(ZonedDateTime now) {
        if (!isWeekday(now.getDayOfWeek())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return !time.isBefore(KRX_OPEN) && time.isBefore(KRX_CLOSE);
    }

    // JPX has a 11:30-12:30 JST lunch break; check both sessions explicitly so the contract
    // (returns true only when the exchange is actually trading) stays precise.
    boolean isJpxOpen(ZonedDateTime now) {
        if (!isWeekday(now.getDayOfWeek())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        boolean morning = !time.isBefore(JPX_MORNING_OPEN) && time.isBefore(JPX_MORNING_CLOSE);
        boolean afternoon =
                !time.isBefore(JPX_AFTERNOON_OPEN) && time.isBefore(JPX_AFTERNOON_CLOSE);
        return morning || afternoon;
    }

    boolean isLoaded() {
        return loaded;
    }
}
