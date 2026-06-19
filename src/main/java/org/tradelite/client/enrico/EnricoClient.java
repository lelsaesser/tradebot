package org.tradelite.client.enrico;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.enrico.dto.EnricoHolidayDto;
import org.tradelite.common.Exchange;

/**
 * Client for the Kayaposoft Enrico public-holidays API ({@code kayaposoft.com/enrico/json/v2.0}).
 * Free, MIT-licensed, no auth, no rate limit. Used by {@code MarketStatusService} to populate
 * per-exchange holiday caches for international exchanges.
 *
 * <p>The {@code holidayType=public_holiday} query parameter filters out observances and other
 * non-statutory entries at the source, so callers can treat every returned date as a market closure
 * (modulo the per-exchange {@link Exchange#getExtras()} overlay for Heiligabend / Silvester / JPX
 * year-end / Stockholm year-end which Enrico doesn't classify as public holidays).
 *
 * <p>Failure mode: returns an empty map and logs at warn. Mirrors {@link
 * org.tradelite.client.finnhub.FinnhubClient#getMarketHolidays} for symmetry — the consuming
 * service falls back to weekday-only checks when the cache is empty.
 *
 * <p>Added in #498.
 */
@Slf4j
@Component
public class EnricoClient {

    private static final String API_URL = "https://kayaposoft.com/enrico/json/v2.0";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final RestTemplate restTemplate;

    @Autowired
    public EnricoClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch every public holiday for the exchange's country between {@code from} and {@code to}
     * inclusive. Maps date → English name (the {@code lang == "en"} entry from the multilingual
     * {@code name} array). Insertion-ordered so callers iterating chronologically observe the
     * upstream order. Returns an empty map on any failure.
     */
    public Map<LocalDate, String> getHolidaysForRange(
            Exchange exchange, LocalDate from, LocalDate to) {
        String url =
                API_URL
                        + "/?action=getHolidaysForDateRange&fromDate="
                        + from.format(DATE_FORMAT)
                        + "&toDate="
                        + to.format(DATE_FORMAT)
                        + "&country="
                        + exchange.getEnricoCountryCode()
                        + "&holidayType=public_holiday";

        ResponseEntity<EnricoHolidayDto[]> response;
        try {
            response = restTemplate.getForEntity(url, EnricoHolidayDto[].class);
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch holidays for {} from Enrico: {}",
                    exchange,
                    e.getClass().getSimpleName());
            return Collections.emptyMap();
        }

        EnricoHolidayDto[] body = response.getBody();
        if (body == null || !response.getStatusCode().is2xxSuccessful()) {
            log.warn(
                    "Failed to fetch holidays for {} from Enrico: HTTP {}",
                    exchange,
                    response.getStatusCode());
            return Collections.emptyMap();
        }

        Map<LocalDate, String> result = new LinkedHashMap<>();
        for (EnricoHolidayDto holiday : body) {
            EnricoHolidayDto.DateDto date = holiday.date();
            if (date == null) {
                continue;
            }
            LocalDate localDate = LocalDate.of(date.year(), date.month(), date.day());
            String name = pickEnglishName(holiday);
            result.put(localDate, name);
        }
        log.info("Loaded {} holidays for {} from Enrico", result.size(), exchange);
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns the English-language name for a holiday, or the first available name if no English
     * entry exists, or a placeholder if the {@code name} array is missing entirely.
     */
    private static String pickEnglishName(EnricoHolidayDto holiday) {
        if (holiday.name() == null || holiday.name().isEmpty()) {
            return "Holiday";
        }
        for (EnricoHolidayDto.NameDto candidate : holiday.name()) {
            if ("en".equalsIgnoreCase(candidate.lang())) {
                return candidate.text();
            }
        }
        return holiday.name().getFirst().text();
    }
}
