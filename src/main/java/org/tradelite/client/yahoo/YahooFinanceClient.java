package org.tradelite.client.yahoo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
    private static final int PROCESS_TIMEOUT_SECONDS = 15;

    private final ObjectMapper objectMapper;
    private final ApiRequestMeteringService meteringService;

    @Autowired
    public YahooFinanceClient(
            ObjectMapper objectMapper, ApiRequestMeteringService meteringService) {
        this.objectMapper = objectMapper;
        this.meteringService = meteringService;
    }

    public List<OhlcvRecord> fetchDailyOhlcv(String symbol, int days) {
        String range = mapDaysToRange(days);
        String url = BASE_URL + symbol + "?interval=1d&range=" + range;

        meteringService.incrementYahooRequests();
        String json = executeCurl(symbol, url);
        return parseResponse(symbol, json);
    }

    @Generated
    String executeCurl(String symbol, String url) {
        List<String> command =
                List.of(
                        "curl",
                        "--fail",
                        "-s",
                        "--connect-timeout",
                        "5",
                        "--max-time",
                        "10",
                        "-H",
                        "User-Agent: Mozilla/5.0",
                        url);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new YahooFetchException(symbol, "curl timed out after 15 seconds");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                throw new YahooFetchException(
                        symbol, "curl exited with code " + exitCode + ": " + output);
            }

            return output;
        } catch (YahooFetchException e) {
            throw e;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new YahooFetchException(symbol, "interrupted");
        } catch (Exception e) {
            throw new YahooFetchException(symbol, e.getMessage());
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
