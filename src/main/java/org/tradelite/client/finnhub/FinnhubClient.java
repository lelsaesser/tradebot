package org.tradelite.client.finnhub;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.utils.DateUtil;

@Component
public class FinnhubClient {

    private static final String API_URL = "https://finnhub.io/api/v1";

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;
    private final TradebotApiProperties apiProperties;
    private final FinnhubFallbackStrategy fallbackStrategy;

    @Autowired
    public FinnhubClient(
            RestTemplate restTemplate,
            ApiRequestMeteringService meteringService,
            TradebotApiProperties apiProperties,
            FinnhubFallbackStrategy fallbackStrategy) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
        this.apiProperties = apiProperties;
        this.fallbackStrategy = fallbackStrategy;
    }

    private String getApiUrl(String baseUrl, StockSymbol ticker) {
        return API_URL
                + String.format(baseUrl, ticker.getTicker())
                + "&token="
                + apiProperties.getFinnhubKey();
    }

    public PriceQuoteResponse getPriceQuote(StockSymbol ticker) {
        if (apiProperties.getFinnhubKey() == null || apiProperties.getFinnhubKey().isBlank()) {
            return fallbackStrategy.onQuoteFailure(
                    ticker, new IllegalStateException("FINNHUB key not configured"));
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
                return fallbackStrategy.onQuoteFailure(
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
            return fallbackStrategy.onQuoteFailure(ticker, e);
        }
    }

    public InsiderTransactionResponse getInsiderTransactions(StockSymbol ticker) {
        if (apiProperties.getFinnhubKey() == null || apiProperties.getFinnhubKey().isBlank()) {
            return fallbackStrategy.onInsiderFailure(
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
                return fallbackStrategy.onInsiderFailure(
                        ticker,
                        new IllegalStateException(
                                "Failed to fetch insider transactions for "
                                        + ticker.getTicker()
                                        + ": "
                                        + response.getStatusCode()));
            }
            return responseBody;
        } catch (Exception e) {
            return fallbackStrategy.onInsiderFailure(ticker, e);
        }
    }
}
