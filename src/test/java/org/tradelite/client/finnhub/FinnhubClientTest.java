package org.tradelite.client.finnhub;

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
import org.tradelite.common.SymbolType;
import org.tradelite.service.ApiRequestMeteringService;

import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinnhubClientTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ApiRequestMeteringService meteringService;

    private FinnhubClient finnhubClient;

    @BeforeEach
    void setUp() {
        finnhubClient = new FinnhubClient(restTemplate, meteringService);
    }

    @Test
    void getPriceQuote_no2xxResponse() {

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(PriceQuoteResponse.class)))
                .thenReturn(ResponseEntity.notFound().build());

        assertThrows(IllegalStateException.class, () -> finnhubClient.getPriceQuote(StockSymbol.META));

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PriceQuoteResponse.class)
        );
    }

    @Test
    void getPriceQuote_restClientException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(PriceQuoteResponse.class)))
                .thenThrow(new RestClientException("Error fetching price quote"));

        assertThrows(RestClientException.class, () -> finnhubClient.getPriceQuote(StockSymbol.META));

        assertThat(StockSymbol.META.getSymbolType(), is(SymbolType.STOCK));

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PriceQuoteResponse.class)
        );
    }

    @Test
    void getPriceQuote_ok() {
        PriceQuoteResponse response = new PriceQuoteResponse();
        response.setCurrentPrice(300.0);
        response.setStockSymbol(StockSymbol.META);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(PriceQuoteResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        PriceQuoteResponse result = finnhubClient.getPriceQuote(StockSymbol.META);

        assertThat(result, notNullValue());
        assertThat(result.getCurrentPrice(), is(300.0));
        assertThat(result.getStockSymbol(), notNullValue());
        assertThat(result.getStockSymbol().getTicker(), is(StockSymbol.META.getTicker()));

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(PriceQuoteResponse.class)
        );
    }

    @Test
    void getInsiderTransactions_ok() {
        InsiderTransactionResponse response = new InsiderTransactionResponse(List.of(
            new InsiderTransactionResponse.Transaction("Alice", 100, 5, "2023-10-02", "2023-10-01", "S", 10200.0)
        ));

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(InsiderTransactionResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        InsiderTransactionResponse result = finnhubClient.getInsiderTransactions(StockSymbol.META);

        assertThat(result, notNullValue());
        assertThat(result.data().size(), is(1));
        assertThat(result.data().getFirst().transactionCode(), is("S"));

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(InsiderTransactionResponse.class)
        );
    }

    @Test
    void getInsiderTransactions_noData() {
        InsiderTransactionResponse response = new InsiderTransactionResponse(List.of());

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(InsiderTransactionResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        InsiderTransactionResponse result = finnhubClient.getInsiderTransactions(StockSymbol.META);

        assertThat(result, notNullValue());
        assertThat(result.data().size(), is(0));

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(InsiderTransactionResponse.class)
        );
    }

    @Test
    void getInsiderTransactions_restClientException() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(InsiderTransactionResponse.class)))
                .thenThrow(new RestClientException("Error fetching insider transactions"));

        assertThrows(RestClientException.class, () -> finnhubClient.getInsiderTransactions(StockSymbol.META));

        verify(restTemplate, times(1)).exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(InsiderTransactionResponse.class)
        );
    }
}
