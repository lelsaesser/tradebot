package org.tradelite.client.coingecko;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.tradelite.client.coingecko.dto.CoinGeckoPriceResponse;
import org.tradelite.common.CoinId;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;

@ExtendWith(MockitoExtension.class)
class CoinGeckoClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ApiRequestMeteringService meteringService;

    private TradebotApiProperties properties;
    private CoinGeckoClient coinGeckoClient;

    @BeforeEach
    void setUp() {
        properties = new TradebotApiProperties();
        properties.setCoingeckoKey("test-key");
        coinGeckoClient = new CoinGeckoClient(restTemplate, meteringService, properties);
    }

    @Test
    void getCoinPriceData_ok() {
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(200000.0);
        CoinGeckoPriceResponse coinDto = new CoinGeckoPriceResponse();
        coinDto.setCoinData(CoinId.BITCOIN.getId(), coinData);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(new ResponseEntity<>(coinDto, HttpStatus.OK));

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);

        assertThat(result, notNullValue());
        assertThat(result.getCoinId(), is(CoinId.BITCOIN));
    }

    @Test
    void getCoinPriceData_no2xx_throws() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> coinGeckoClient.getCoinPriceData(CoinId.BITCOIN));

        assertThat(exception.getMessage(), is("Failed to fetch coin price data: 404 NOT_FOUND"));
    }

    @Test
    void getCoinPriceData_restClientException_throws() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenThrow(new RestClientException("Network error"));

        RestClientException exception =
                assertThrows(
                        RestClientException.class,
                        () -> coinGeckoClient.getCoinPriceData(CoinId.BITCOIN));

        assertThat(exception.getMessage(), is("Network error"));
    }

    @Test
    void getCoinPriceData_missingPayload_throws() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(new ResponseEntity<>(new CoinGeckoPriceResponse(), HttpStatus.OK));

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> coinGeckoClient.getCoinPriceData(CoinId.BITCOIN));

        assertThat(exception.getMessage(), is("CoinGecko response missing payload for bitcoin"));
    }

    @Test
    void getCoinPriceData_missingKey_throws() {
        properties.setCoingeckoKey("");

        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> coinGeckoClient.getCoinPriceData(CoinId.BITCOIN));

        assertThat(exception.getMessage(), is("COINGECKO key not configured"));
        verifyNoInteractions(restTemplate);
    }
}
