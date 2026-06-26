package org.tradelite.service;

import jakarta.annotation.PostConstruct;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.tradelite.client.enrico.EnricoClient;
import org.tradelite.client.finnhub.FinnhubClient;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse.MarketHoliday;
import org.tradelite.common.Exchange;

@Slf4j
@Service
public class MarketStatusService {

    static final ZoneId NY_ZONE = ZoneId.of("America/New_York");
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
    private static final LocalTime STO_OPEN = LocalTime.of(9, 0);
    private static final LocalTime STO_CLOSE = LocalTime.of(17, 30);
    private static final LocalTime PAR_OPEN = LocalTime.of(9, 0);
    private static final LocalTime PAR_CLOSE = LocalTime.of(17, 30);

    /** Forward window covered by Enrico fetches at startup; renews on every redeploy. */
    private static final int HOLIDAY_FETCH_WINDOW_DAYS = 365;

    private final FinnhubClient finnhubClient;
    private final EnricoClient enricoClient;

    /** NYSE holidays (Finnhub). Carries early-close metadata via {@link MarketHoliday}. */
    private final AtomicReference<Map<LocalDate, MarketHoliday>> holidayCache =
            new AtomicReference<>(Collections.emptyMap());

    private volatile boolean loaded = false;

    /** International exchange holidays (Enrico). Map<Exchange, Map<LocalDate, holiday-name>>. */
    private final AtomicReference<Map<Exchange, Map<LocalDate, String>>> internationalHolidayCache =
            new AtomicReference<>(Collections.emptyMap());

    /** Per-exchange loaded flags so one flaky country code doesn't block the others. */
    private final Map<Exchange, AtomicBoolean> internationalLoaded;

    public MarketStatusService(FinnhubClient finnhubClient, EnricoClient enricoClient) {
        this.finnhubClient = finnhubClient;
        this.enricoClient = enricoClient;
        EnumMap<Exchange, AtomicBoolean> loadedMap = new EnumMap<>(Exchange.class);
        for (Exchange e : Exchange.values()) {
            loadedMap.put(e, new AtomicBoolean(false));
        }
        this.internationalLoaded = Collections.unmodifiableMap(loadedMap);
    }

    @PostConstruct
    void loadHolidays() {
        fetchAndCacheHolidays();
        loadInternationalHolidays();
    }

    @Scheduled(fixedDelay = 300_000)
    void retryIfNeeded() {
        if (!loaded) {
            log.warn("Holiday cache is empty, retrying fetch...");
            fetchAndCacheHolidays();
        }
    }

    @Scheduled(fixedDelay = 300_000)
    void retryInternationalHolidaysIfNeeded() {
        for (Exchange exchange : Exchange.values()) {
            if (!internationalLoaded.get(exchange).get()) {
                log.warn("International holiday cache empty for {}, retrying fetch...", exchange);
                fetchAndCacheInternationalHolidays(exchange);
            }
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

    private void loadInternationalHolidays() {
        for (Exchange exchange : Exchange.values()) {
            fetchAndCacheInternationalHolidays(exchange);
        }
    }

    private void fetchAndCacheInternationalHolidays(Exchange exchange) {
        LocalDate from = LocalDate.now(exchange.getZoneId());
        LocalDate to = from.plusDays(HOLIDAY_FETCH_WINDOW_DAYS);
        Map<LocalDate, String> holidays = enricoClient.getHolidaysForRange(exchange, from, to);
        if (holidays.isEmpty()) {
            // EnricoClient already logged the cause; leave loaded flag false so retry kicks in.
            return;
        }

        // Replace the per-exchange entry while preserving entries for other exchanges.
        Map<Exchange, Map<LocalDate, String>> next = new EnumMap<>(Exchange.class);
        next.putAll(internationalHolidayCache.get());
        next.put(exchange, holidays);
        internationalHolidayCache.set(Collections.unmodifiableMap(next));
        internationalLoaded.get(exchange).set(true);
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

    /**
     * Returns the holiday name for any international exchange whose today's date is a holiday
     * (Enrico-supplied or {@link Exchange#getExtras()} overlay). Used by the daily Telegram
     * notification — exchanges that are open today simply don't appear in the result.
     */
    public Map<Exchange, String> getTodayInternationalHolidays() {
        EnumMap<Exchange, String> result = new EnumMap<>(Exchange.class);
        for (Exchange exchange : Exchange.values()) {
            LocalDate today = LocalDate.now(exchange.getZoneId());
            String name = lookupHolidayName(exchange, today);
            if (name != null) {
                result.put(exchange, name);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    public boolean isExchangeOpen(String symbol) {
        if (symbol == null) {
            return false;
        }
        Optional<Exchange> exchange = Exchange.fromTicker(symbol);
        if (exchange.isEmpty()) {
            log.warn(
                    "No exchange mapping found for symbol: {} — skipping price evaluation", symbol);
            return false;
        }
        return isOpen(exchange.get(), ZonedDateTime.now(exchange.get().getZoneId()));
    }

    /** Holiday-aware open check. Combines weekday + holiday + per-exchange time-window logic. */
    boolean isOpen(Exchange exchange, ZonedDateTime now) {
        if (!isWeekday(now.getDayOfWeek())) {
            return false;
        }
        if (isHoliday(exchange, now.toLocalDate())) {
            return false;
        }
        LocalTime time = now.toLocalTime();
        return switch (exchange) {
            case XETRA -> !time.isBefore(XETRA_OPEN) && time.isBefore(XETRA_CLOSE);
            case KRX -> !time.isBefore(KRX_OPEN) && time.isBefore(KRX_CLOSE);
            case JPX -> isJpxTradingTime(time);
            case STO -> !time.isBefore(STO_OPEN) && time.isBefore(STO_CLOSE);
            case PAR -> !time.isBefore(PAR_OPEN) && time.isBefore(PAR_CLOSE);
        };
    }

    /**
     * JPX has a 11:30-12:30 JST lunch break; check both sessions explicitly so the contract returns
     * true only when the exchange is actually trading.
     */
    private static boolean isJpxTradingTime(LocalTime time) {
        boolean morning = !time.isBefore(JPX_MORNING_OPEN) && time.isBefore(JPX_MORNING_CLOSE);
        boolean afternoon =
                !time.isBefore(JPX_AFTERNOON_OPEN) && time.isBefore(JPX_AFTERNOON_CLOSE);
        return morning || afternoon;
    }

    boolean isHoliday(Exchange exchange, LocalDate date) {
        return lookupHolidayName(exchange, date) != null;
    }

    /**
     * Returns the holiday name from either the Enrico cache or the {@link Exchange#getExtras()}
     * overlay, or {@code null} if {@code date} is a regular trading day.
     */
    private String lookupHolidayName(Exchange exchange, LocalDate date) {
        if (internationalLoaded.get(exchange).get()) {
            String enricoName = internationalHolidayCache.get().get(exchange).get(date);
            if (enricoName != null) {
                return enricoName;
            }
        }
        if (exchange.getExtras().contains(MonthDay.from(date))) {
            return "Exchange closure";
        }
        return null;
    }

    boolean isLoaded() {
        return loaded;
    }
}
