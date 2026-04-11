package org.tradelite.client.finnhub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
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

    private TradebotApiProperties properties;
    private FinnhubClient finnhubClient;

    @BeforeEach
    void setUp() {
        properties = new TradebotApiProperties();
        properties.setFinnhubKey("test-key");
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties);
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
    }

    @Test
    void getPriceQuote_no2xxResponse_throws() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenReturn(ResponseEntity.notFound().build());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> finnhubClient.getPriceQuote(ticker));

        assertThat(
                exception.getMessage(), is("Failed to fetch price quote for META: 404 NOT_FOUND"));
    }

    @Test
    void getPriceQuote_restClientException_throws() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenThrow(new RestClientException("Error fetching price quote"));

        RestClientException exception =
                assertThrows(RestClientException.class, () -> finnhubClient.getPriceQuote(ticker));

        assertThat(exception.getMessage(), is("Error fetching price quote"));
    }

    @Test
    void getPriceQuote_missingKey_throws() {
        StockSymbol ticker = new StockSymbol("AAPL", "Apple");
        properties.setFinnhubKey("");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> finnhubClient.getPriceQuote(ticker));

        assertThat(exception.getMessage(), is("FINNHUB key not configured"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getPriceQuote_nullBody_throws() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenReturn(ResponseEntity.ok().build());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class, () -> finnhubClient.getPriceQuote(ticker));

        assertThat(exception.getMessage(), is("Failed to fetch price quote for META: 200 OK"));
    }

    @Test
    void getInsiderTransactions_ok() {
        InsiderTransactionResponse response =
                new InsiderTransactionResponse(
                        List.of(
                                new InsiderTransactionResponse.Transaction(
                                        "Alice",
                                        100,
                                        5,
                                        "2023-10-02",
                                        "2023-10-01",
                                        "S",
                                        10200.0)));

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
    }

    @Test
    void getInsiderTransactions_failure_throws() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenThrow(new RestClientException("Error fetching insider transactions"));

        RestClientException exception =
                assertThrows(
                        RestClientException.class,
                        () -> finnhubClient.getInsiderTransactions(ticker));

        assertThat(exception.getMessage(), is("Error fetching insider transactions"));
    }

    @Test
    void getInsiderTransactions_missingKey_throws() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");
        properties.setFinnhubKey(" ");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> finnhubClient.getInsiderTransactions(ticker));

        assertThat(exception.getMessage(), is("FINNHUB key not configured"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void getInsiderTransactions_no2xxResponse_throws() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenReturn(ResponseEntity.notFound().build());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> finnhubClient.getInsiderTransactions(ticker));

        assertThat(
                exception.getMessage(),
                is("Failed to fetch insider transactions for META: 404 NOT_FOUND"));
    }

    @Test
    void getInsiderTransactions_nullBody_throws() {
        StockSymbol ticker = new StockSymbol("META", "Meta Platforms");

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenReturn(ResponseEntity.ok().build());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> finnhubClient.getInsiderTransactions(ticker));

        assertThat(
                exception.getMessage(),
                is("Failed to fetch insider transactions for META: 200 OK"));
    }

    @Test
    void toRuntime_wrapsCheckedExceptions() throws Exception {
        Method toRuntime = FinnhubClient.class.getDeclaredMethod("toRuntime", Exception.class);
        toRuntime.setAccessible(true);

        Object result = toRuntime.invoke(finnhubClient, new Exception("checked"));

        assertThat(result, is(instanceOf(IllegalStateException.class)));
        assertThat(((IllegalStateException) result).getMessage(), is("checked"));
    }
}
