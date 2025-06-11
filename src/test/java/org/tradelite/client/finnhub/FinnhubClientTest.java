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
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;

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

    private FinnhubClient finnhubClient;

    @BeforeEach
    void setUp() {
        finnhubClient = new FinnhubClient(restTemplate);
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
}
