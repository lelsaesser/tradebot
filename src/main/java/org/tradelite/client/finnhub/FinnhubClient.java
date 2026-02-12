package org.tradelite.client.finnhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.utils.DateUtil;

@Slf4j
@Component
public class FinnhubClient {

    private static final String API_URL = "https://finnhub.io/api/v1";

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;
    private final TradebotApiProperties apiProperties;
    private final ObjectMapper objectMapper;

    @Autowired
    public FinnhubClient(
            RestTemplate restTemplate,
            ApiRequestMeteringService meteringService,
            TradebotApiProperties apiProperties,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
        this.apiProperties = apiProperties;
        this.objectMapper = objectMapper;
    }

    private String getApiUrl(String baseUrl, StockSymbol ticker) {
        return API_URL
                + String.format(baseUrl, ticker.getTicker())
                + "&token="
                + apiProperties.getFinnhubKey();
    }

    public PriceQuoteResponse getPriceQuote(StockSymbol ticker) {
        if (apiProperties.getFinnhubKey() == null || apiProperties.getFinnhubKey().isBlank()) {
            return fallbackPriceQuote(ticker, new IllegalStateException("FINNHUB key not configured"));
        }

        String baseUrl = "/quote?symbol=%s";
        String url = getApiUrl(baseUrl, ticker);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            meteringService.incrementFinnhubRequests();
            ResponseEntity<PriceQuoteResponse> response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, requestEntity, PriceQuoteResponse.class);
            PriceQuoteResponse quote = response.getBody();
            if (quote == null || !response.getStatusCode().is2xxSuccessful()) {
                return fallbackPriceQuote(
                        ticker,
                        new IllegalStateException(
                                "Failed to fetch price quote for "
                                        + ticker.getTicker()
                                        + ": "
                                        + response.getStatusCode()));
            }
            quote.setStockSymbol(ticker);
            return quote;
        } catch (Exception e) {
            return fallbackPriceQuote(ticker, e);
        }
    }

    public InsiderTransactionResponse getInsiderTransactions(StockSymbol ticker) {
        if (apiProperties.getFinnhubKey() == null || apiProperties.getFinnhubKey().isBlank()) {
            return fallbackInsiderTransactions(
                    ticker, new IllegalStateException("FINNHUB key not configured"));
        }

        String fromDate = DateUtil.getDateTwoMonthsAgo(null);
        String baseUrl = "/stock/insider-transactions?symbol=%s";
        String url = getApiUrl(baseUrl, ticker);
        url = url + "&from=" + fromDate;

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            meteringService.incrementFinnhubRequests();
            ResponseEntity<InsiderTransactionResponse> response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, requestEntity, InsiderTransactionResponse.class);
            InsiderTransactionResponse responseBody = response.getBody();
            if (responseBody == null || !response.getStatusCode().is2xxSuccessful()) {
                return fallbackInsiderTransactions(
                        ticker,
                        new IllegalStateException(
                                "Failed to fetch insider transactions for "
                                        + ticker.getTicker()
                                        + ": "
                                        + response.getStatusCode()));
            }
            return responseBody;
        } catch (Exception e) {
            return fallbackInsiderTransactions(ticker, e);
        }
    }

    private PriceQuoteResponse fallbackPriceQuote(StockSymbol ticker, Exception original) {
        if (!apiProperties.isFixtureFallbackEnabled()) {
            log.error(original.getMessage(), original);
            throw unwrapRuntimeException(original);
        }

        return loadFixture(
                "finnhub/quote",
                ticker.getTicker(),
                PriceQuoteResponse.class,
                original,
                fixture -> {
                    fixture.setStockSymbol(ticker);
                    return fixture;
                });
    }

    private InsiderTransactionResponse fallbackInsiderTransactions(
            StockSymbol ticker, Exception original) {
        if (!apiProperties.isFixtureFallbackEnabled()) {
            log.error(original.getMessage(), original);
            throw unwrapRuntimeException(original);
        }

        return loadFixture(
                "finnhub/insider",
                ticker.getTicker(),
                InsiderTransactionResponse.class,
                original,
                fixture -> fixture);
    }

    private <T> T loadFixture(
            String subPath,
            String symbolOrId,
            Class<T> type,
            Exception original,
            java.util.function.Function<T, T> mapper) {
        Path symbolPath =
                Path.of(apiProperties.getFixtureBasePath(), subPath, symbolOrId.toUpperCase() + ".json");
        Path defaultPath = Path.of(apiProperties.getFixtureBasePath(), subPath, "default.json");
        Path selectedPath = Files.exists(symbolPath) ? symbolPath : defaultPath;

        try {
            T loaded = objectMapper.readValue(selectedPath.toFile(), type);
            if (!Files.exists(symbolPath)) {
                log.warn(
                        "Falling back to default fixture {} for {} after API failure: {}",
                        selectedPath,
                        symbolOrId,
                        original.getMessage());
            } else {
                log.warn(
                        "Using fixture {} for {} after API failure: {}",
                        selectedPath,
                        symbolOrId,
                        original.getMessage());
            }
            return mapper.apply(loaded);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed API call and fixture lookup for " + symbolOrId + " in " + selectedPath, e);
        }
    }

    private RuntimeException unwrapRuntimeException(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }
}
