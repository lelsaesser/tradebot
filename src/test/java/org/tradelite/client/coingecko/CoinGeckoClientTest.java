package org.tradelite.client.coingecko;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

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
import org.tradelite.service.ApiRequestMeteringService;

@ExtendWith(MockitoExtension.class)
class CoinGeckoClientTest {

    @Mock private RestTemplate restTemplate;

    @Mock private ApiRequestMeteringService meteringService;

    private CoinGeckoClient coinGeckoClient;

    @BeforeEach
    void setUp() {
        coinGeckoClient = new CoinGeckoClient(restTemplate, meteringService);
    }

    @Test
    void testGetCoinPriceData_ok() {
        CoinGeckoPriceResponse.CoinData coinData = new CoinGeckoPriceResponse.CoinData();
        coinData.setUsd(200000.0);
        coinData.setCoinId(CoinId.BITCOIN);
        CoinGeckoPriceResponse coinDto = new CoinGeckoPriceResponse();
        coinDto.setCoinData(CoinId.BITCOIN.getId(), coinData);

        ResponseEntity<CoinGeckoPriceResponse> response =
                new ResponseEntity<>(coinDto, HttpStatus.OK);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(response);

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);

        assertThat(result, notNullValue());
    }

    @Test
    void testGetCoinPriceData_no2xxResponse() {
        ResponseEntity<CoinGeckoPriceResponse> response =
                new ResponseEntity<>(null, HttpStatus.NOT_FOUND);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(response);

        assertThrows(
                IllegalStateException.class,
                () -> {
                    coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);
                });
    }

    @Test
    void testGetCoinPriceData_restClientException() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenThrow(new RestClientException("Network error"));

        assertThrows(
                RestClientException.class,
                () -> {
                    coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);
                });
    }
}
