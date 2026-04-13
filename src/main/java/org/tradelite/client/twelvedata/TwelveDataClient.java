package org.tradelite.client.twelvedata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;

@Slf4j
@Component
public class TwelveDataClient {

    private static final String BASE_URL = "https://api.twelvedata.com/time_series";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApiRequestMeteringService meteringService;
    private final TradebotApiProperties apiProperties;

    @Autowired
    public TwelveDataClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            ApiRequestMeteringService meteringService,
            TradebotApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.meteringService = meteringService;
        this.apiProperties = apiProperties;
    }

    public List<OhlcvRecord> fetchDailyOhlcv(String symbol, int outputSize) {
        if (apiProperties.getTwelvedataKey() == null
                || apiProperties.getTwelvedataKey().isBlank()) {
            throw fetchFailure(symbol, new IllegalStateException("TWELVEDATA key not configured"));
        }

        String url =
                String.format(
                        "%s?symbol=%s&interval=1day&outputsize=%d&apikey=%s",
                        BASE_URL, symbol, outputSize, apiProperties.getTwelvedataKey());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/json");
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response;
        try {
            meteringService.incrementTwelveDataRequests();
            response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            throw fetchFailure(symbol, e);
        }

        String body = response.getBody();
        if (body == null) {
            throw fetchFailure(symbol, new IllegalStateException("Twelve Data returned null body"));
        }

        return parseResponse(symbol, body);
    }

    List<OhlcvRecord> parseResponse(String symbol, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            if ("error".equals(root.path("status").asText())) {
                String message = root.path("message").asText("Unknown API error");
                throw new IllegalStateException(
                        "Twelve Data API error for " + symbol + ": " + message);
            }

            JsonNode values = root.path("values");
            if (!values.isArray()) {
                throw new IllegalStateException(
                        "Twelve Data response missing values array for " + symbol);
            }

            List<OhlcvRecord> records = new ArrayList<>();
            for (JsonNode entry : values) {
                String datetime = entry.path("datetime").asText(null);
                JsonNode openNode = entry.path("open");
                JsonNode highNode = entry.path("high");
                JsonNode lowNode = entry.path("low");
                JsonNode closeNode = entry.path("close");
                JsonNode volumeNode = entry.path("volume");

                if (datetime == null
                        || openNode.isMissingNode()
                        || highNode.isMissingNode()
                        || lowNode.isMissingNode()
                        || closeNode.isMissingNode()
                        || volumeNode.isMissingNode()) {
                    continue;
                }

                records.add(
                        new OhlcvRecord(
                                symbol,
                                LocalDate.parse(datetime),
                                Double.parseDouble(openNode.asText()),
                                Double.parseDouble(highNode.asText()),
                                Double.parseDouble(lowNode.asText()),
                                Double.parseDouble(closeNode.asText()),
                                Long.parseLong(volumeNode.asText())));
            }

            log.debug("Parsed {} OHLCV records for {} from Twelve Data", records.size(), symbol);
            return records;

        } catch (Exception e) {
            throw fetchFailure(symbol, e);
        }
    }

    private RuntimeException fetchFailure(String symbol, Exception cause) {
        log.error("Failed to fetch Twelve Data OHLCV for {}", symbol, cause);
        return toRuntime(cause);
    }

    private RuntimeException toRuntime(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }
}
