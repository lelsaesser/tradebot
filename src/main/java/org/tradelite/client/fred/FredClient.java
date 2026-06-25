package org.tradelite.client.fred;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.fred.dto.FredObservationDto;
import org.tradelite.client.fred.dto.FredObservationsResponseDto;
import org.tradelite.config.TradebotApiProperties;

/**
 * Client for the FRED® (Federal Reserve Economic Data) API. Free with an API key, single-string
 * auth via the {@code api_key} query parameter. Used by {@code TreasuryTracker} to fetch the four
 * US Treasury macro signals (T10Y3M, T10Y2Y, DFII10, THREEFYTP10).
 *
 * <p>API key is read from {@link TradebotApiProperties#getFredKey()} which resolves from the {@code
 * FRED_API_KEY} env var (or {@code FRED_DEV_API_KEY} in dev profile). Empty key is accepted
 * silently — failures surface as HTTP 400 / empty-list-with-WARN at request time, matching the
 * existing pattern for Finnhub / TwelveData API keys.
 *
 * <p>FRED's wire format uses the literal {@code "."} sentinel for "no observation today" (weekends,
 * holidays, pending publication). This client filters those rows at the boundary so consumers
 * receive only real observations.
 *
 * <p>Failure mode: returns an empty list and logs at warn. Mirrors {@link
 * org.tradelite.client.enrico.EnricoClient#getHolidaysForRange} for symmetry.
 *
 * <p>FRED ToS requires attribution. The required notice ("This product uses the FRED® API but is
 * not endorsed or certified by the Federal Reserve Bank of St. Louis.") is rendered in the daily
 * Telegram report footer by {@code TreasuryTracker}, not here — this client is a transport layer
 * unaware of presentation.
 *
 * <p>Added in #516.
 */
@Slf4j
@Component
public class FredClient {

    private static final String API_URL = "https://api.stlouisfed.org/fred/series/observations";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String MISSING_VALUE_SENTINEL = ".";

    private final RestTemplate restTemplate;
    private final String apiKey;

    @Autowired
    public FredClient(RestTemplate restTemplate, TradebotApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.apiKey = apiProperties.getFredKey();
    }

    /**
     * Fetch all observations for {@code seriesId} between {@code from} and {@code to} inclusive.
     * Results are sorted date-descending (per the URL's {@code sort_order=desc} parameter); callers
     * typically use {@code list.getFirst()} for the most recent observation. Rows with FRED's
     * {@code "."} no-data sentinel are filtered out. Returns an empty list on any failure.
     */
    public List<FredObservation> fetchObservations(String seriesId, LocalDate from, LocalDate to) {
        String url =
                API_URL
                        + "?series_id="
                        + seriesId
                        + "&api_key="
                        + apiKey
                        + "&file_type=json"
                        + "&sort_order=desc"
                        + "&observation_start="
                        + from.format(DATE_FORMAT)
                        + "&observation_end="
                        + to.format(DATE_FORMAT);

        ResponseEntity<FredObservationsResponseDto> response;
        try {
            response = restTemplate.getForEntity(url, FredObservationsResponseDto.class);
        } catch (Exception e) {
            log.warn(
                    "Failed to fetch observations for {} from FRED: {}",
                    seriesId,
                    e.getClass().getSimpleName());
            return Collections.emptyList();
        }

        FredObservationsResponseDto body = response.getBody();
        if (body == null || !response.getStatusCode().is2xxSuccessful()) {
            log.warn(
                    "Failed to fetch observations for {} from FRED: HTTP {}",
                    seriesId,
                    response.getStatusCode());
            return Collections.emptyList();
        }

        List<FredObservationDto> raw = body.observations();
        if (raw == null || raw.isEmpty()) {
            log.warn("FRED returned no observations for {}", seriesId);
            return Collections.emptyList();
        }

        List<FredObservation> result = new ArrayList<>();
        for (FredObservationDto row : raw) {
            if (row.value() == null || MISSING_VALUE_SENTINEL.equals(row.value())) {
                continue;
            }
            try {
                LocalDate date = LocalDate.parse(row.date(), DATE_FORMAT);
                double value = Double.parseDouble(row.value());
                result.add(new FredObservation(date, value));
            } catch (Exception e) {
                log.warn(
                        "Skipping unparseable FRED row for {}: date={}, value={}, error={}",
                        seriesId,
                        row.date(),
                        row.value(),
                        e.getClass().getSimpleName());
            }
        }
        log.info("Loaded {} observations for {} from FRED", result.size(), seriesId);
        return Collections.unmodifiableList(result);
    }
}
