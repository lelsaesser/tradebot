package org.tradelite.client.finnhub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.finnhub.dto.InsiderTransactionResponse;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;

@ExtendWith(MockitoExtension.class)
class FinnhubClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ApiRequestMeteringService meteringService;
    @Mock private FinnhubFallbackStrategy fallbackStrategy;

    private TradebotApiProperties properties;
    private FinnhubClient finnhubClient;

    @BeforeEach
    void setUp() {
        properties = new TradebotApiProperties();
        properties.setFinnhubKey("test-key");
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties, fallbackStrategy);
    }

    @Test
    void getPriceQuote_ok() {
        PriceQuoteResponse response = new PriceQuoteResponse();
        response.setCurrentPrice(300.0);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        PriceQuoteResponse result =
                finnhubClient.getPriceQuote(new StockSymbol("META", "Meta Platforms"));

        assertThat(result, notNullValue());
        assertThat(result.getCurrentPrice(), is(300.0));
        assertThat(result.getStockSymbol(), notNullValue());
        verifyNoInteractions(fallbackStrategy);
    }

    @Test
    void getPriceQuote_no2xxResponse_delegatesToFallbackStrategy() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");
        PriceQuoteResponse fallback = new PriceQuoteResponse();
        fallback.setCurrentPrice(101.0);
        fallback.setStockSymbol(ticker);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenReturn(ResponseEntity.notFound().build());
        when(fallbackStrategy.onQuoteFailure(eq(ticker), any(Exception.class))).thenReturn(fallback);

        PriceQuoteResponse result = finnhubClient.getPriceQuote(ticker);

        assertThat(result.getCurrentPrice(), is(101.0));
        verify(fallbackStrategy, times(1)).onQuoteFailure(eq(ticker), any(Exception.class));
    }

    @Test
    void getPriceQuote_restClientException_delegatesToFallbackStrategy() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");
        PriceQuoteResponse fallback = new PriceQuoteResponse();
        fallback.setCurrentPrice(102.0);
        fallback.setStockSymbol(ticker);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenThrow(new RestClientException("Error fetching price quote"));
        when(fallbackStrategy.onQuoteFailure(eq(ticker), any(Exception.class))).thenReturn(fallback);

        PriceQuoteResponse result = finnhubClient.getPriceQuote(ticker);

        assertThat(result.getCurrentPrice(), is(102.0));
        verify(fallbackStrategy, times(1)).onQuoteFailure(eq(ticker), any(Exception.class));
    }

    @Test
    void getPriceQuote_missingKey_delegatesToFallbackStrategy() {
        StockSymbol ticker = new StockSymbol("AAPL", "Apple");
        properties.setFinnhubKey("");

        PriceQuoteResponse fallback = new PriceQuoteResponse();
        fallback.setCurrentPrice(99.0);
        fallback.setStockSymbol(ticker);
        when(fallbackStrategy.onQuoteFailure(eq(ticker), any(Exception.class))).thenReturn(fallback);

        PriceQuoteResponse result = finnhubClient.getPriceQuote(ticker);

        assertThat(result.getCurrentPrice(), is(99.0));
        verify(fallbackStrategy, times(1)).onQuoteFailure(eq(ticker), any(Exception.class));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getInsiderTransactions_ok() {
        InsiderTransactionResponse response =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice", 100, 5, "2023-10-02", "2023-10-01", "S", 10200.0)));

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        InsiderTransactionResponse result =
                finnhubClient.getInsiderTransactions(new StockSymbol("META", "Meta Platforms"));

        assertThat(result, notNullValue());
        assertThat(result.data().size(), is(1));
        verifyNoInteractions(fallbackStrategy);
    }

    @Test
    void getInsiderTransactions_failure_delegatesToFallbackStrategy() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");
        InsiderTransactionResponse fallback = new InsiderTransactionResponse(List.of());

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenThrow(new RestClientException("Error fetching insider transactions"));
        when(fallbackStrategy.onInsiderFailure(eq(ticker), any(Exception.class))).thenReturn(fallback);

        InsiderTransactionResponse result = finnhubClient.getInsiderTransactions(ticker);

        assertThat(result, notNullValue());
        verify(fallbackStrategy, times(1)).onInsiderFailure(eq(ticker), any(Exception.class));
    }
}
