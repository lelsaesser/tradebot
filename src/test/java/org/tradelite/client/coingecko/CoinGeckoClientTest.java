package org.tradelite.client.coingecko;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private CoinGeckoClient coinGeckoClient;

    @BeforeEach
    void setUp() {
        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setCoingeckoKey("test-key");
        properties.setFixtureFallbackEnabled(false);
        coinGeckoClient =
                new CoinGeckoClient(restTemplate, meteringService, properties, new ObjectMapper());
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
                ResponseEntity.status(HttpStatus.NOT_FOUND).build();

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

    @Test
    void testGetCoinPriceData_fallbackToFixtureWhenLiveFails() throws Exception {
        Path fixtureBase = Files.createTempDirectory("coingecko-fixtures");
        Path priceDir = fixtureBase.resolve("coingecko/price");
        Files.createDirectories(priceDir);
        Files.writeString(priceDir.resolve("default.json"), "{\"usd\":123.45,\"usd_24h_change\":4.2}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setCoingeckoKey("test-key");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        coinGeckoClient =
                new CoinGeckoClient(restTemplate, meteringService, properties, new ObjectMapper());

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenThrow(new RestClientException("429"));

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);

        assertThat(result, notNullValue());
        assertThat(result.getUsd(), org.hamcrest.Matchers.is(123.45));
    }

    @Test
    void testGetCoinPriceData_missingKeyWithFallbackEnabled_usesFixture() throws Exception {
        Path fixtureBase = Files.createTempDirectory("coingecko-fixtures");
        Path priceDir = fixtureBase.resolve("coingecko/price");
        Files.createDirectories(priceDir);
        Files.writeString(priceDir.resolve("default.json"), "{\"usd\":99.0,\"usd_24h_change\":0.5}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setCoingeckoKey("");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        coinGeckoClient =
                new CoinGeckoClient(restTemplate, meteringService, properties, new ObjectMapper());

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);
        assertThat(result.getUsd(), org.hamcrest.Matchers.is(99.0));
    }

    @Test
    void testGetCoinPriceData_missingKeyWithFallbackDisabled_throws() {
        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setCoingeckoKey("");
        properties.setFixtureFallbackEnabled(false);
        coinGeckoClient =
                new CoinGeckoClient(restTemplate, meteringService, properties, new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> coinGeckoClient.getCoinPriceData(CoinId.BITCOIN));
    }

    @Test
    void testGetCoinPriceData_missingFixture_throws() throws Exception {
        Path fixtureBase = Files.createTempDirectory("coingecko-fixtures-empty");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setCoingeckoKey("");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        coinGeckoClient =
                new CoinGeckoClient(restTemplate, meteringService, properties, new ObjectMapper());

        assertThrows(IllegalStateException.class, () -> coinGeckoClient.getCoinPriceData(CoinId.BITCOIN));
    }

    @Test
    void testGetCoinPriceData_missingCoinPayload_fallsBackToFixture() throws Exception {
        Path fixtureBase = Files.createTempDirectory("coingecko-fixtures");
        Path priceDir = fixtureBase.resolve("coingecko/price");
        Files.createDirectories(priceDir);
        Files.writeString(priceDir.resolve("default.json"), "{\"usd\":77.0,\"usd_24h_change\":-1.5}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setCoingeckoKey("test-key");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        coinGeckoClient =
                new CoinGeckoClient(restTemplate, meteringService, properties, new ObjectMapper());

        CoinGeckoPriceResponse emptyResponse = new CoinGeckoPriceResponse();
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(CoinGeckoPriceResponse.class)))
                .thenReturn(new ResponseEntity<>(emptyResponse, HttpStatus.OK));

        CoinGeckoPriceResponse.CoinData result = coinGeckoClient.getCoinPriceData(CoinId.BITCOIN);
        assertThat(result.getUsd(), org.hamcrest.Matchers.is(77.0));
    }
}
