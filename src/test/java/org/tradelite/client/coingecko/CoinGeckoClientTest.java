package org.tradelite.client.coingecko;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
    @Mock private CoinGeckoFallbackStrategy fallbackStrategy;

    private TradebotApiProperties properties;
    private CoinGeckoClient coinGeckoClient;

    @BeforeEach
    void setUp() {
        properties = new TradebotApiProperties();
        properties.setCoingeckoKey("test-key");
        coinGeckoClient =
                new CoinGeckoClient(restTemplate, meteringService, properties, fallbackStrategy);
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
        verifyNoInteractions(fallbackStrategy);
    }

    @Test
    void getCoinPriceData_no2xx_delegatesToFallbackStrategy() {
        CoinGeckoPriceResponse.CoinData fallback = new CoinGeckoPriceResponse.CoinData();
        fallback.setUsd(77.0);
        fallback.setCoinId(CoinId.BITCOIN);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
        when(fallbackStrategy.onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class)))
                .thenReturn(fallback);

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);

        assertThat(result.getUsd(), is(77.0));
        verify(fallbackStrategy, times(1)).onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class));
    }

    @Test
    void getCoinPriceData_restClientException_delegatesToFallbackStrategy() {
        CoinGeckoPriceResponse.CoinData fallback = new CoinGeckoPriceResponse.CoinData();
        fallback.setUsd(88.0);
        fallback.setCoinId(CoinId.BITCOIN);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenThrow(new RestClientException("Network error"));
        when(fallbackStrategy.onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class)))
                .thenReturn(fallback);

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);

        assertThat(result.getUsd(), is(88.0));
        verify(fallbackStrategy, times(1)).onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class));
    }

    @Test
    void getCoinPriceData_missingPayload_delegatesToFallbackStrategy() {
        CoinGeckoPriceResponse.CoinData fallback = new CoinGeckoPriceResponse.CoinData();
        fallback.setUsd(66.0);
        fallback.setCoinId(CoinId.BITCOIN);

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(new ResponseEntity<>(new CoinGeckoPriceResponse(), HttpStatus.OK));
        when(fallbackStrategy.onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class)))
                .thenReturn(fallback);

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);

        assertThat(result.getUsd(), is(66.0));
        verify(fallbackStrategy, times(1)).onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class));
    }

    @Test
    void getCoinPriceData_missingKey_delegatesToFallbackStrategy() {
        properties.setCoingeckoKey("");
        CoinGeckoPriceResponse.CoinData fallback = new CoinGeckoPriceResponse.CoinData();
        fallback.setUsd(55.0);
        fallback.setCoinId(CoinId.BITCOIN);
        when(fallbackStrategy.onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class)))
                .thenReturn(fallback);

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);

        assertThat(result.getUsd(), is(55.0));
        verify(fallbackStrategy, times(1)).onPriceFailure(eq(CoinId.BITCOIN), any(Exception.class));
        verifyNoInteractions(restTemplate);
    }
}
