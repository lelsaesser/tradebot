package org.tradelite.client.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.yahoo.dto.YahooOhlcvRecord;
import org.tradelite.service.ApiRequestMeteringService;

@Slf4j
@Component
public class YahooFinanceClient {

    private static final String CHART_URL =
            "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=%s&interval=1d";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)"
                    + " AppleWebKit/537.36 (KHTML, like Gecko)"
                    + " Chrome/120.0.0.0 Safari/537.36";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiRequestMeteringService meteringService;

    @Autowired
    public YahooFinanceClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            ApiRequestMeteringService meteringService) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.meteringService = meteringService;
    }

    /**
     * Fetches daily OHLCV data from Yahoo Finance for the given symbol and time range.
     *
     * <p>Yahoo's chart API with {@code interval=1d} returns one bar per trading day. The {@code
     * range} parameter controls how far back to look (e.g. "6mo" for 6 months of history, "5d" for
     * the last 5 trading days).
     *
     * <p>Note on "5d" range: Yahoo's "1d" range returns intraday minute-level candles, not a single
     * daily OHLCV bar. Using "5d" with {@code interval=1d} reliably returns the last ~5 daily bars
     * including today's running bar. The extra 4 days are harmless — upsert ({@code INSERT OR
     * REPLACE}) overwrites identical existing records.
     */
    public List<YahooOhlcvRecord> fetchDailyOhlcv(String symbol, String range) {
        String url = String.format(CHART_URL, symbol, range);

        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", USER_AGENT);
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

        ResponseEntity<String> response;
        try {
            meteringService.incrementYahooRequests();
            response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
        } catch (HttpClientErrorException.TooManyRequests _) {
            log.warn("Yahoo Finance rate limited (HTTP 429) for symbol {}", symbol);
            return Collections.emptyList();
        } catch (RestClientException e) {
            log.warn("Failed to fetch Yahoo Finance data for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }

        String body = response.getBody();
        if (body == null) {
            log.warn("Yahoo Finance returned null body for symbol {}", symbol);
            return Collections.emptyList();
        }

        return parseResponse(symbol, body);
    }

    List<YahooOhlcvRecord> parseResponse(String symbol, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) {
                log.warn("Yahoo Finance returned empty result for symbol {}", symbol);
                return Collections.emptyList();
            }

            JsonNode firstResult = result.get(0);
            JsonNode timestamps = firstResult.path("timestamp");
            JsonNode quote = firstResult.path("indicators").path("quote");
            if (!quote.isArray() || quote.isEmpty()) {
                log.warn("Yahoo Finance returned empty quote data for symbol {}", symbol);
                return Collections.emptyList();
            }

            JsonNode quoteData = quote.get(0);
            JsonNode opens = quoteData.path("open");
            JsonNode highs = quoteData.path("high");
            JsonNode lows = quoteData.path("low");
            JsonNode closes = quoteData.path("close");
            JsonNode volumes = quoteData.path("volume");

            JsonNode adjCloses = extractAdjClose(firstResult);

            if (!timestamps.isArray() || timestamps.isEmpty()) {
                log.warn("Yahoo Finance returned empty timestamps for symbol {}", symbol);
                return Collections.emptyList();
            }

            List<YahooOhlcvRecord> records = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                if (isNullOrMissing(opens, i)
                        || isNullOrMissing(highs, i)
                        || isNullOrMissing(lows, i)
                        || isNullOrMissing(closes, i)
                        || isNullOrMissing(volumes, i)) {
                    continue;
                }

                long epochSeconds = timestamps.get(i).asLong();
                LocalDate date =
                        Instant.ofEpochSecond(epochSeconds)
                                .atZone(ZoneId.of("America/New_York"))
                                .toLocalDate();

                double adjClose =
                        (adjCloses != null && !isNullOrMissing(adjCloses, i))
                                ? adjCloses.get(i).asDouble()
                                : closes.get(i).asDouble();

                records.add(
                        new YahooOhlcvRecord(
                                symbol,
                                date,
                                opens.get(i).asDouble(),
                                highs.get(i).asDouble(),
                                lows.get(i).asDouble(),
                                closes.get(i).asDouble(),
                                adjClose,
                                volumes.get(i).asLong()));
            }

            log.debug("Parsed {} OHLCV records for {} from Yahoo Finance", records.size(), symbol);
            return records;

        } catch (Exception e) {
            log.warn("Failed to parse Yahoo Finance response for {}: {}", symbol, e.getMessage());
            return Collections.emptyList();
        }
    }

    private JsonNode extractAdjClose(JsonNode firstResult) {
        JsonNode adjCloseArray = firstResult.path("indicators").path("adjclose");
        if (adjCloseArray.isArray() && !adjCloseArray.isEmpty()) {
            return adjCloseArray.get(0).path("adjclose");
        }
        return null;
    }

    private boolean isNullOrMissing(JsonNode array, int index) {
        return !array.isArray()
                || index >= array.size()
                || array.get(index) == null
                || array.get(index).isNull();
    }
}
