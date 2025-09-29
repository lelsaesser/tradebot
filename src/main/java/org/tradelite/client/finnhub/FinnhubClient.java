package org.tradelite.client.finnhub;

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
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.utils.DateUtil;

@Slf4j
@Component
public class FinnhubClient {

    private static final String API_URL = "https://finnhub.io/api/v1";
    private static final String API_KEY = System.getenv("FINNHUB_API_KEY");

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;

    @Autowired
    public FinnhubClient(RestTemplate restTemplate, ApiRequestMeteringService meteringService) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
    }

    private String getApiUrl(String baseUrl, StockSymbol ticker) {
        return API_URL + String.format(baseUrl, ticker.getTicker()) + "&token=" + API_KEY;
    }

    public PriceQuoteResponse getPriceQuote(StockSymbol ticker) {
        String baseUrl = "/quote?symbol=%s";
        String url = getApiUrl(baseUrl, ticker);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            meteringService.incrementFinnhubRequests();
            ResponseEntity<PriceQuoteResponse> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, PriceQuoteResponse.class);
            PriceQuoteResponse quote = response.getBody();
            if (quote == null || !response.getStatusCode().is2xxSuccessful()) {
                throw new IllegalStateException("Failed to fetch price quote for " + ticker.getTicker() + ": " + response.getStatusCode());
            }
            quote.setStockSymbol(ticker);
            return quote;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }

    public InsiderTransactionResponse getInsiderTransactions(StockSymbol ticker) {
        String fromDate = DateUtil.getDateTwoMonthsAgo(null);
        String baseUrl = "/stock/insider-transactions?symbol=%s";
        String url = getApiUrl(baseUrl, ticker);
        url = url + "&from=" + fromDate;

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        try {
            meteringService.incrementFinnhubRequests();
            ResponseEntity<InsiderTransactionResponse> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, InsiderTransactionResponse.class);
            return response.getBody();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw e;
        }
    }
}
