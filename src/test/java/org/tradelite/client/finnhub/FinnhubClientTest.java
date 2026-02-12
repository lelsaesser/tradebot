package org.tradelite.client.finnhub;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;

@ExtendWith(MockitoExtension.class)
class FinnhubClientTest {

    @Mock private RestTemplate restTemplate;

    @Mock private ApiRequestMeteringService meteringService;

    private FinnhubClient finnhubClient;

    @BeforeEach
    void setUp() {
        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFinnhubKey("test-key");
        properties.setFixtureFallbackEnabled(false);
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties, new ObjectMapper());
    }

    @Test
    void getPriceQuote_no2xxResponse() {

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenReturn(ResponseEntity.notFound().build());

        assertThrows(
                IllegalStateException.class,
                () -> finnhubClient.getPriceQuote(new StockSymbol("META", "Meta Platforms")));

        verify(restTemplate, times(1))
                .exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class));
    }

    @Test
    void getPriceQuote_restClientException() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenThrow(new RestClientException("Error fetching price quote"));

        assertThrows(
                RestClientException.class,
                () -> finnhubClient.getPriceQuote(new StockSymbol("META", "Meta Platforms")));

        assertThat(new StockSymbol("META", "Meta Platforms").getSymbolType(), is(SymbolType.STOCK));

        verify(restTemplate, times(1))
                .exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class));
    }

    @Test
    void getPriceQuote_ok() {
        PriceQuoteResponse response = new PriceQuoteResponse();
        response.setCurrentPrice(300.0);
        response.setStockSymbol(new StockSymbol("META", "Meta Platforms"));

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
        assertThat(
                result.getStockSymbol().getTicker(),
                is(new StockSymbol("META", "Meta Platforms").getTicker()));

        verify(restTemplate, times(1))
                .exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class));
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
        assertThat(result.data().getFirst().transactionCode(), is("S"));

        verify(restTemplate, times(1))
                .exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class));
    }

    @Test
    void getInsiderTransactions_noData() {
        InsiderTransactionResponse response = new InsiderTransactionResponse(List.of());

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenReturn(ResponseEntity.ok(response));

        InsiderTransactionResponse result =
                finnhubClient.getInsiderTransactions(new StockSymbol("META", "Meta Platforms"));

        assertThat(result, notNullValue());
        assertThat(result.data().size(), is(0));

        verify(restTemplate, times(1))
                .exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class));
    }

    @Test
    void getInsiderTransactions_restClientException() {
        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenThrow(new RestClientException("Error fetching insider transactions"));

        assertThrows(
                RestClientException.class,
                () ->
                        finnhubClient.getInsiderTransactions(
                                new StockSymbol("META", "Meta Platforms")));

        verify(restTemplate, times(1))
                .exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class));
    }

    @Test
    void getPriceQuote_fallsBackToFixtureWhenLiveApiFails() throws Exception {
        Path fixtureBase = Files.createTempDirectory("finnhub-fixtures");
        Path quoteDir = fixtureBase.resolve("finnhub/quote");
        Files.createDirectories(quoteDir);
        Files.writeString(
                quoteDir.resolve("default.json"),
                """
                {"c":101.0,"o":100.0,"h":102.0,"l":99.0,"d":1.0,"dp":1.0,"pc":100.0}
                """);

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFinnhubKey("test-key");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties, new ObjectMapper());

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(PriceQuoteResponse.class)))
                .thenThrow(new RestClientException("quota"));

        PriceQuoteResponse result =
                finnhubClient.getPriceQuote(new StockSymbol("META", "Meta Platforms"));

        assertThat(result.getCurrentPrice(), is(101.0));
    }

    @Test
    void getPriceQuote_missingKeyWithFallbackEnabled_usesFixture() throws Exception {
        Path fixtureBase = Files.createTempDirectory("finnhub-fixtures");
        Path quoteDir = fixtureBase.resolve("finnhub/quote");
        Files.createDirectories(quoteDir);
        Files.writeString(
                quoteDir.resolve("default.json"),
                """
                {"c":88.0,"o":87.0,"h":90.0,"l":86.0,"d":1.0,"dp":1.1,"pc":87.0}
                """);

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFinnhubKey("");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties, new ObjectMapper());

        PriceQuoteResponse result =
                finnhubClient.getPriceQuote(new StockSymbol("AAPL", "Apple"));

        assertThat(result.getCurrentPrice(), is(88.0));
    }

    @Test
    void getPriceQuote_missingKeyWithFallbackDisabled_throws() {
        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFinnhubKey("");
        properties.setFixtureFallbackEnabled(false);
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties, new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () -> finnhubClient.getPriceQuote(new StockSymbol("AAPL", "Apple")));
    }

    @Test
    void getInsiderTransactions_fallsBackToFixtureWhenLiveApiFails() throws Exception {
        Path fixtureBase = Files.createTempDirectory("finnhub-fixtures-insider");
        Path insiderDir = fixtureBase.resolve("finnhub/insider");
        Files.createDirectories(insiderDir);
        Files.writeString(insiderDir.resolve("default.json"), "{\"data\":[{\"name\":\"Alice\",\"share\":1,\"change\":1,\"filingDate\":\"2025-01-01\",\"transactionDate\":\"2025-01-01\",\"transactionCode\":\"S\",\"transactionPrice\":11.0}]}");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFinnhubKey("test-key");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties, new ObjectMapper());

        when(restTemplate.exchange(
                        anyString(),
                        eq(HttpMethod.GET),
                        any(HttpEntity.class),
                        eq(InsiderTransactionResponse.class)))
                .thenThrow(new RestClientException("quota"));

        InsiderTransactionResponse result =
                finnhubClient.getInsiderTransactions(new StockSymbol("META", "Meta Platforms"));

        assertThat(result.data().size(), is(1));
    }

    @Test
    void getInsiderTransactions_missingFixture_throws() throws Exception {
        Path fixtureBase = Files.createTempDirectory("finnhub-fixtures-empty");

        TradebotApiProperties properties = new TradebotApiProperties();
        properties.setFinnhubKey("");
        properties.setFixtureFallbackEnabled(true);
        properties.setFixtureBasePath(fixtureBase.toString());
        finnhubClient = new FinnhubClient(restTemplate, meteringService, properties, new ObjectMapper());

        assertThrows(
                IllegalStateException.class,
                () -> finnhubClient.getInsiderTransactions(new StockSymbol("META", "Meta Platforms")));
    }
}
