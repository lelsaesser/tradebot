package org.tradelite.client.finnhub;

import java.util.Optional;
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
import org.tradelite.client.finnhub.dto.EarningsCalendarResponse;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.MarketHolidayResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.utils.DateUtil;

@Slf4j
@Component
public class FinnhubClient {

    private static final String API_URL = "https://finnhub.io/api/v1";

    private final RestTemplate restTemplate;
    private final ApiRequestMeteringService meteringService;
    private final TradebotApiProperties apiProperties;

    @Autowired
    public FinnhubClient(
            RestTemplate restTemplate,
            ApiRequestMeteringService meteringService,
            TradebotApiProperties apiProperties) {
        this.restTemplate = restTemplate;
        this.meteringService = meteringService;
        this.apiProperties = apiProperties;
    }

    private String getApiUrl(String baseUrl, StockSymbol ticker) {
        return API_URL
                + String.format(baseUrl, ticker.getTicker())
                + "&token="
                + apiProperties.getFinnhubKey();
    }

    public PriceQuoteResponse getPriceQuote(StockSymbol ticker) {
        try {
            return fetchPriceQuote(ticker);
        } catch (Exception e) {
            throw quoteFailure(ticker, e);
        }
    }

    /**
     * Attempts to fetch a price quote without raising or logging at ERROR. Intended for callers
     * (e.g. ticker validation in `/add`) where an unknown symbol is an expected outcome rather than
     * an error condition. Returns {@link Optional#empty()} on any failure and logs at INFO.
     */
    public Optional<PriceQuoteResponse> tryGetPriceQuote(StockSymbol ticker) {
        try {
            return Optional.of(fetchPriceQuote(ticker));
        } catch (Exception e) {
            log.info(
                    "Finnhub quote unavailable for ticker {}: {}",
                    ticker.getTicker(),
                    e.getMessage());
            return Optional.empty();
        }
    }

    private PriceQuoteResponse fetchPriceQuote(StockSymbol ticker) {
        if (apiProperties.getFinnhubKey() == null || apiProperties.getFinnhubKey().isBlank()) {
            throw new IllegalStateException("FINNHUB key not configured");
        }

        String url = getApiUrl("/quote?symbol=%s", ticker);
        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(new LinkedMultiValueMap<>(), new HttpHeaders());

        meteringService.incrementFinnhubRequests();
        ResponseEntity<PriceQuoteResponse> response =
                restTemplate.exchange(url, HttpMethod.GET, requestEntity, PriceQuoteResponse.class);

        PriceQuoteResponse quote = response.getBody();
        if (quote == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException(
                    "Failed to fetch price quote for "
                            + ticker.getTicker()
                            + ": "
                            + response.getStatusCode());
        }
        quote.setStockSymbol(ticker);
        return quote;
    }

    public InsiderTransactionResponse getInsiderTransactions(StockSymbol ticker) {
        if (apiProperties.getFinnhubKey() == null || apiProperties.getFinnhubKey().isBlank()) {
            throw insiderFailure(ticker, new IllegalStateException("FINNHUB key not configured"));
        }

        String fromDate = DateUtil.getDateTwoMonthsAgo(null);
        String baseUrl = "/stock/insider-transactions?symbol=%s";
        String url = getApiUrl(baseUrl, ticker);
        url = url + "&from=" + fromDate;

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        HttpHeaders headers = new HttpHeaders();
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        ResponseEntity<InsiderTransactionResponse> response;
        try {
            meteringService.incrementFinnhubRequests();
            response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, requestEntity, InsiderTransactionResponse.class);
        } catch (Exception e) {
            throw insiderFailure(ticker, e);
        }

        InsiderTransactionResponse responseBody = response.getBody();
        if (responseBody == null || !response.getStatusCode().is2xxSuccessful()) {
            throw insiderFailure(
                    ticker,
                    new IllegalStateException(
                            "Failed to fetch insider transactions for "
                                    + ticker.getTicker()
                                    + ": "
                                    + response.getStatusCode()));
        }
        return responseBody;
    }

    public MarketHolidayResponse getMarketHolidays() {
        String url =
                API_URL
                        + "/stock/market-holiday?exchange=US&token="
                        + apiProperties.getFinnhubKey();

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(new LinkedMultiValueMap<>(), new HttpHeaders());

        ResponseEntity<MarketHolidayResponse> response;
        try {
            meteringService.incrementFinnhubRequests();
            response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, requestEntity, MarketHolidayResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch market holidays from Finnhub", e);
            return null;
        }

        MarketHolidayResponse body = response.getBody();
        if (body == null || !response.getStatusCode().is2xxSuccessful()) {
            log.error("Failed to fetch market holidays: {}", response.getStatusCode());
            return null;
        }
        return body;
    }

    public EarningsCalendarResponse getEarningsCalendar(String from, String to) {
        String url =
                API_URL
                        + "/calendar/earnings?from="
                        + from
                        + "&to="
                        + to
                        + "&token="
                        + apiProperties.getFinnhubKey();

        HttpEntity<MultiValueMap<String, Object>> requestEntity =
                new HttpEntity<>(new LinkedMultiValueMap<>(), new HttpHeaders());

        ResponseEntity<EarningsCalendarResponse> response;
        try {
            meteringService.incrementFinnhubRequests();
            response =
                    restTemplate.exchange(
                            url, HttpMethod.GET, requestEntity, EarningsCalendarResponse.class);
        } catch (Exception e) {
            log.error("Failed to fetch earnings calendar from Finnhub", e);
            return null;
        }

        EarningsCalendarResponse body = response.getBody();
        if (body == null || !response.getStatusCode().is2xxSuccessful()) {
            log.error("Failed to fetch earnings calendar: {}", response.getStatusCode());
            return null;
        }
        return body;
    }

    private RuntimeException quoteFailure(StockSymbol ticker, Exception cause) {
        log.error("Failed to fetch Finnhub quote for {}", ticker.getTicker(), cause);
        return toRuntime(cause);
    }

    private RuntimeException insiderFailure(StockSymbol ticker, Exception cause) {
        log.error("Failed to fetch Finnhub insider transactions for {}", ticker.getTicker(), cause);
        return toRuntime(cause);
    }

    private RuntimeException toRuntime(Exception exception) {
        if (exception instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        return new IllegalStateException(exception.getMessage(), exception);
    }
}
