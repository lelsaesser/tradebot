package org.tradelite.client;

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
import org.tradelite.client.dto.InsiderTransactionResponse;
import org.tradelite.client.dto.PriceQuoteResponse;
import org.tradelite.common.StockTicker;

@Slf4j
@Component
public class FinnhubClient {

    private static final String API_URL = "https://finnhub.io/api/v1";
    private static final String API_KEY = System.getenv("FINNHUB_API_KEY");

    private final RestTemplate restTemplate;

    @Autowired
    public FinnhubClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private String getApiUrl(String baseUrl, StockTicker ticker) {
        return API_URL + String.format(baseUrl, ticker.getTicker()) + "&token=" + API_KEY;
    }

    public void getFinancialData(StockTicker ticker) {
        String baseUrl = "/stock/metric?symbol=%s&metric=all";
        String url = getApiUrl(baseUrl, ticker);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            log.info(response.getBody());
        } catch (Exception e) {
            log.error("Error fetching financial data: {}", e.getMessage());
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public InsiderTransactionResponse getInsiderTransactions(StockTicker ticker) {
        String fromDate = "2025-04-15";
        String baseUrl = "/stock/insider-transactions?symbol=%s";
        String url = getApiUrl(baseUrl, ticker);
        url = url + "&from=" + fromDate;

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<InsiderTransactionResponse> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, InsiderTransactionResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }

    public PriceQuoteResponse getPriceQuote(StockTicker ticker) {
        String baseUrl = "/quote?symbol=%s";
        String url = getApiUrl(baseUrl, ticker);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<PriceQuoteResponse> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, PriceQuoteResponse.class);
            PriceQuoteResponse quote = response.getBody();
            if (quote == null) {
                throw new IllegalStateException("Response body is null");
            }
            quote.setStockTicker(ticker);
            return quote;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new IllegalStateException(e.getMessage(), e);
        }
    }
}
