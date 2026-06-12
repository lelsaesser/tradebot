package org.tradelite.client.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.service.ApiRequestMeteringService;

@Slf4j
@Component
public class YahooFinanceClient {

    private static final String BASE_URL = "https://query1.finance.yahoo.com/v8/finance/chart/";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final String USER_AGENT = "Mozilla/5.0";

    private final ObjectMapper objectMapper;
    private final ApiRequestMeteringService meteringService;
    private final HttpClient yahooHttpClient;

    @Autowired
    public YahooFinanceClient(
            ObjectMapper objectMapper,
            ApiRequestMeteringService meteringService,
            HttpClient yahooHttpClient) {
        this.objectMapper = objectMapper;
        this.meteringService = meteringService;
        this.yahooHttpClient = yahooHttpClient;
    }

    public List<OhlcvRecord> fetchDailyOhlcv(String symbol, int days) {
        String range = mapDaysToRange(days);
        String url = BASE_URL + symbol + "?interval=1d&range=" + range;

        meteringService.incrementYahooRequests();
        String json = executeRequest(symbol, url);
        return parseResponse(symbol, json);
    }

    public YahooPriceQuote fetchCurrentPrice(String symbol) {
        String url = BASE_URL + symbol + "?interval=1d&range=1d";

        meteringService.incrementYahooRequests();
        String json = executeRequest(symbol, url);
        return parseQuoteFromMeta(symbol, json);
    }

    YahooPriceQuote parseQuoteFromMeta(String symbol, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode error = root.path("chart").path("error");
            if (!error.isNull() && !error.isMissingNode()) {
                throw new YahooFetchException(
                        symbol, "API error: " + error.path("description").asText("unknown"));
            }

            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) {
                throw new YahooFetchException(symbol, "no result in response");
            }

            JsonNode meta = result.get(0).path("meta");
            double currentPrice = meta.path("regularMarketPrice").asDouble(0);
            double previousClose = meta.path("chartPreviousClose").asDouble(0);
            double dailyOpen = meta.path("regularMarketOpen").asDouble(0);
            double dailyHigh = meta.path("regularMarketDayHigh").asDouble(0);
            double dailyLow = meta.path("regularMarketDayLow").asDouble(0);
            long timestamp = meta.path("regularMarketTime").asLong(0);

            if (currentPrice <= 0) {
                throw new YahooFetchException(
                        symbol, "invalid regularMarketPrice: " + currentPrice);
            }

            double changePercent = 0;
            if (previousClose > 0) {
                changePercent = ((currentPrice - previousClose) / previousClose) * 100;
            }

            return new YahooPriceQuote(
                    symbol,
                    currentPrice,
                    previousClose,
                    dailyOpen,
                    dailyHigh,
                    dailyLow,
                    changePercent,
                    timestamp);
        } catch (YahooFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new YahooFetchException(symbol, "JSON parse error: " + e.getMessage());
        }
    }

    /**
     * HTTP transport via {@link java.net.http.HttpClient}. On non-2xx, the exception message
     * includes status code, full response headers, and full response body so failure forensics are
     * unambiguous. On I/O failure, the exception message includes the exception class simple name
     * (e.g. {@code SSLHandshakeException}, {@code ConnectException}) so transport-level failure
     * modes are visible without DEBUG logging.
     *
     * <p>Verified in production over a 2-week window after #435 introduced the path: no
     * SSL/TLS-related failures observed, disproving the original "Yahoo blocks Java HTTP clients
     * via TLS fingerprinting" hypothesis. The legacy ProcessBuilder + curl path was removed in
     * #457.
     */
    @Generated
    String executeRequest(String symbol, String url) {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("User-Agent", USER_AGENT)
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
        try {
            HttpResponse<String> response =
                    yahooHttpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new YahooFetchException(
                        symbol,
                        "HTTP "
                                + response.statusCode()
                                + " headers="
                                + response.headers().map()
                                + " body="
                                + response.body());
            }
            return response.body();
        } catch (YahooFetchException e) {
            throw e;
        } catch (HttpTimeoutException _) {
            throw new YahooFetchException(symbol, "request timed out after 15 seconds");
        } catch (IOException e) {
            throw new YahooFetchException(
                    symbol, "I/O error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new YahooFetchException(symbol, "interrupted");
        }
    }

    List<OhlcvRecord> parseResponse(String symbol, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            JsonNode error = root.path("chart").path("error");
            if (!error.isNull() && !error.isMissingNode()) {
                throw new YahooFetchException(
                        symbol, "API error: " + error.path("description").asText("unknown"));
            }

            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) {
                throw new YahooFetchException(symbol, "no result in response");
            }

            JsonNode firstResult = result.get(0);
            String timezone = firstResult.path("meta").path("exchangeTimezoneName").asText("UTC");
            ZoneId zoneId = ZoneId.of(timezone);

            JsonNode timestamps = firstResult.path("timestamp");
            JsonNode indicators = firstResult.path("indicators").path("quote");
            if (!timestamps.isArray() || !indicators.isArray() || indicators.isEmpty()) {
                throw new YahooFetchException(symbol, "missing timestamp or quote data");
            }

            JsonNode quote = indicators.get(0);
            JsonNode opens = quote.path("open");
            JsonNode highs = quote.path("high");
            JsonNode lows = quote.path("low");
            JsonNode closes = quote.path("close");
            JsonNode volumes = quote.path("volume");

            List<OhlcvRecord> records = new ArrayList<>();
            for (int i = 0; i < timestamps.size(); i++) {
                JsonNode openNode = opens.get(i);
                JsonNode highNode = highs.get(i);
                JsonNode lowNode = lows.get(i);
                JsonNode closeNode = closes.get(i);
                JsonNode volumeNode = volumes.get(i);

                if (openNode == null
                        || openNode.isNull()
                        || highNode == null
                        || highNode.isNull()
                        || lowNode == null
                        || lowNode.isNull()
                        || closeNode == null
                        || closeNode.isNull()
                        || volumeNode == null
                        || volumeNode.isNull()) {
                    continue;
                }

                long epochSeconds = timestamps.get(i).asLong();
                LocalDate date = Instant.ofEpochSecond(epochSeconds).atZone(zoneId).toLocalDate();

                records.add(
                        new OhlcvRecord(
                                symbol,
                                date,
                                openNode.asDouble(),
                                highNode.asDouble(),
                                lowNode.asDouble(),
                                closeNode.asDouble(),
                                volumeNode.asLong()));
            }

            log.debug("Parsed {} OHLCV records for {} from Yahoo Finance", records.size(), symbol);
            return records;

        } catch (YahooFetchException e) {
            throw e;
        } catch (Exception e) {
            throw new YahooFetchException(symbol, "JSON parse error: " + e.getMessage());
        }
    }

    static String mapDaysToRange(int days) {
        if (days <= 5) {
            return "5d";
        } else if (days <= 30) {
            return "1mo";
        } else {
            return "2y";
        }
    }
}
