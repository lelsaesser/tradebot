package org.tradelite.client.twelvedata;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.config.TradebotApiProperties;
import org.tradelite.service.ApiRequestMeteringService;

@ExtendWith(MockitoExtension.class)
class TwelveDataClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ApiRequestMeteringService meteringService;
    @Mock private TradebotApiProperties apiProperties;

    private TwelveDataClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        lenient().when(apiProperties.getTwelvedataKey()).thenReturn("test-api-key");
        client = new TwelveDataClient(restTemplate, objectMapper, meteringService, apiProperties);
    }

    @Test
    void fetchDailyOhlcv_successfulResponse_returnsRecords() {
        String json = buildValidResponse();

        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        List<OhlcvRecord> records = client.fetchDailyOhlcv("XLK", 5);

        assertThat(records, hasSize(2));
        assertThat(records.getFirst().symbol(), is("XLK"));
        assertThat(records.getFirst().date(), is(LocalDate.of(2026, 4, 10)));
        assertThat(records.getFirst().open(), is(closeTo(218.50, 0.01)));
        assertThat(records.getFirst().high(), is(closeTo(220.00, 0.01)));
        assertThat(records.getFirst().low(), is(closeTo(217.00, 0.01)));
        assertThat(records.getFirst().close(), is(closeTo(219.50, 0.01)));
        assertThat(records.getFirst().volume(), is(5000000L));

        assertThat(records.get(1).symbol(), is("XLK"));
        assertThat(records.get(1).date(), is(LocalDate.of(2026, 4, 9)));
        assertThat(records.get(1).open(), is(closeTo(215.00, 0.01)));

        verify(meteringService).incrementTwelveDataRequests();
    }

    @Test
    void fetchDailyOhlcv_http429_throwsException() {
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(
                        HttpClientErrorException.create(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too Many Requests",
                                new org.springframework.http.HttpHeaders(),
                                new byte[0],
                                null));

        assertThrows(HttpClientErrorException.class, () -> client.fetchDailyOhlcv("XLK", 5));

        verify(meteringService).incrementTwelveDataRequests();
    }

    @Test
    void fetchDailyOhlcv_networkError_throwsException() {
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThrows(RestClientException.class, () -> client.fetchDailyOhlcv("XLK", 5));

        verify(meteringService).incrementTwelveDataRequests();
    }

    @Test
    void fetchDailyOhlcv_apiErrorResponse_throwsException() {
        String errorJson =
                """
                {"status":"error","code":400,"message":"**symbol** not found: INVALID. Please specify it correctly according to API Documentation."}
                """;

        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(errorJson));

        IllegalStateException ex =
                assertThrows(
                        IllegalStateException.class, () -> client.fetchDailyOhlcv("INVALID", 5));

        assertThat(ex.getMessage(), containsString("Twelve Data API error"));
        assertThat(ex.getMessage(), containsString("INVALID"));
    }

    @Test
    void fetchDailyOhlcv_nullBody_throwsException() {
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(null));

        assertThrows(IllegalStateException.class, () -> client.fetchDailyOhlcv("XLK", 5));
    }

    @Test
    void fetchDailyOhlcv_nullValuesInArray_skipsIncomplete() {
        String json =
                """
                {
                    "status":"ok",
                    "values":[
                        {"datetime":"2026-04-10","open":"218.50","high":"220.00","low":"217.00","close":"219.50","volume":"5000000"},
                        {"datetime":"2026-04-09","high":"218.00","low":"215.00","close":"217.00","volume":"4000000"}
                    ]
                }
                """;

        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        List<OhlcvRecord> records = client.fetchDailyOhlcv("XLK", 5);

        assertThat(records, hasSize(1));
        assertThat(records.getFirst().close(), is(closeTo(219.50, 0.01)));
    }

    @Test
    void fetchDailyOhlcv_emptyValuesArray_returnsEmptyList() {
        String json =
                """
                {"status":"ok","values":[]}
                """;

        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        List<OhlcvRecord> records = client.fetchDailyOhlcv("XLK", 5);

        assertThat(records, is(empty()));
    }

    @Test
    void fetchDailyOhlcv_incrementsMeter() {
        String json = buildValidResponse();

        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        client.fetchDailyOhlcv("XLK", 5);

        verify(meteringService, times(1)).incrementTwelveDataRequests();
    }

    private String buildValidResponse() {
        return """
                {
                    "meta":{"symbol":"XLK","interval":"1day","currency":"USD","exchange_timezone":"America/New_York","type":"ETF"},
                    "values":[
                        {"datetime":"2026-04-10","open":"218.50","high":"220.00","low":"217.00","close":"219.50","volume":"5000000"},
                        {"datetime":"2026-04-09","open":"215.00","high":"218.00","low":"214.00","close":"217.50","volume":"4500000"}
                    ],
                    "status":"ok"
                }
                """;
    }
}
