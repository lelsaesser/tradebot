package org.tradelite.client.yahoo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
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
import org.tradelite.client.yahoo.dto.YahooOhlcvRecord;
import org.tradelite.service.ApiRequestMeteringService;

@ExtendWith(MockitoExtension.class)
class YahooFinanceClientTest {

    @Mock private RestTemplate restTemplate;
    @Mock private ApiRequestMeteringService meteringService;

    private YahooFinanceClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        client = new YahooFinanceClient(restTemplate, objectMapper, meteringService);
    }

    @Test
    void fetchDailyOhlcv_successfulResponse_returnsRecords() {
        String json = buildValidResponse();

        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(json));

        List<YahooOhlcvRecord> records = client.fetchDailyOhlcv("AAPL");

        assertThat(records, hasSize(2));
        assertThat(records.getFirst().symbol(), is("AAPL"));
        assertThat(records.getFirst().open(), is(closeTo(170.0, 0.01)));
        assertThat(records.getFirst().high(), is(closeTo(175.0, 0.01)));
        assertThat(records.getFirst().low(), is(closeTo(168.0, 0.01)));
        assertThat(records.getFirst().close(), is(closeTo(174.0, 0.01)));
        assertThat(records.get(0).adjClose(), is(closeTo(173.5, 0.01)));
        assertThat(records.get(0).volume(), is(50000000L));

        assertThat(records.get(1).symbol(), is("AAPL"));
        assertThat(records.get(1).open(), is(closeTo(174.0, 0.01)));
        verify(meteringService).incrementYahooRequests();
    }

    @Test
    void fetchDailyOhlcv_http429_returnsEmptyList() {
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(
                        HttpClientErrorException.create(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "Too Many Requests",
                                new org.springframework.http.HttpHeaders(),
                                new byte[0],
                                null));

        List<YahooOhlcvRecord> records = client.fetchDailyOhlcv("AAPL");

        assertThat(records, is(empty()));
        verify(meteringService).incrementYahooRequests();
    }

    @Test
    void fetchDailyOhlcv_networkError_returnsEmptyList() {
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        List<YahooOhlcvRecord> records = client.fetchDailyOhlcv("AAPL");

        assertThat(records, is(empty()));
        verify(meteringService).incrementYahooRequests();
    }

    @Test
    void fetchDailyOhlcv_nullBody_returnsEmptyList() {
        when(restTemplate.exchange(
                        anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(null));

        List<YahooOhlcvRecord> records = client.fetchDailyOhlcv("AAPL");

        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_emptyResult_returnsEmptyList() {
        String json =
                """
                {"chart":{"result":[]}}
                """;

        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", json);

        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_missingQuoteData_returnsEmptyList() {
        String json =
                """
                {"chart":{"result":[{"timestamp":[1712700000],"indicators":{"quote":[]}}]}}
                """;

        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", json);

        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_malformedJson_returnsEmptyList() {
        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", "not valid json {{{");

        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_nullValueInArraySkipped() {
        String json =
                """
                {"chart":{"result":[{
                    "timestamp":[1712700000, 1712786400],
                    "indicators":{
                        "quote":[{
                            "open":[170.0, null],
                            "high":[175.0, 180.0],
                            "low":[168.0, 172.0],
                            "close":[174.0, 178.0],
                            "volume":[50000000, 60000000]
                        }],
                        "adjclose":[{"adjclose":[173.5, 177.5]}]
                    }
                }]}}
                """;

        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", json);

        assertThat(records, hasSize(1));
        assertThat(records.getFirst().close(), is(closeTo(174.0, 0.01)));
    }

    @Test
    void parseResponse_missingAdjClose_usesClosePrice() {
        String json =
                """
                {"chart":{"result":[{
                    "timestamp":[1712700000],
                    "indicators":{
                        "quote":[{
                            "open":[170.0],
                            "high":[175.0],
                            "low":[168.0],
                            "close":[174.0],
                            "volume":[50000000]
                        }]
                    }
                }]}}
                """;

        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", json);

        assertThat(records, hasSize(1));
        assertThat(records.getFirst().adjClose(), is(closeTo(174.0, 0.01)));
    }

    @Test
    void parseResponse_emptyTimestamps_returnsEmptyList() {
        String json =
                """
                {"chart":{"result":[{
                    "timestamp":[],
                    "indicators":{
                        "quote":[{
                            "open":[],
                            "high":[],
                            "low":[],
                            "close":[],
                            "volume":[]
                        }]
                    }
                }]}}
                """;

        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", json);

        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_convertsTimestampToNewYorkDate() {
        // 2026-04-10 at 14:00 UTC = 2026-04-10 at 10:00 EDT
        long timestamp =
                LocalDate.of(2026, 4, 10)
                        .atTime(14, 0)
                        .atZone(java.time.ZoneId.of("UTC"))
                        .toEpochSecond();

        String json =
                String.format(
                        """
                {"chart":{"result":[{
                    "timestamp":[%d],
                    "indicators":{
                        "quote":[{
                            "open":[170.0],
                            "high":[175.0],
                            "low":[168.0],
                            "close":[174.0],
                            "volume":[50000000]
                        }],
                        "adjclose":[{"adjclose":[173.5]}]
                    }
                }]}}
                """,
                        timestamp);

        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", json);

        assertThat(records, hasSize(1));
        assertThat(records.getFirst().date(), is(LocalDate.of(2026, 4, 10)));
    }

    @Test
    void parseResponse_missingChartNode_returnsEmptyList() {
        String json =
                """
                {"error":"not found"}
                """;

        List<YahooOhlcvRecord> records = client.parseResponse("AAPL", json);

        assertThat(records, is(empty()));
    }

    private String buildValidResponse() {
        // Two trading days of data
        // 2026-04-09 at 14:00 UTC and 2026-04-10 at 14:00 UTC
        long ts1 =
                LocalDate.of(2026, 4, 9)
                        .atTime(14, 0)
                        .atZone(java.time.ZoneId.of("UTC"))
                        .toEpochSecond();
        long ts2 =
                LocalDate.of(2026, 4, 10)
                        .atTime(14, 0)
                        .atZone(java.time.ZoneId.of("UTC"))
                        .toEpochSecond();

        return String.format(
                """
                {"chart":{"result":[{
                    "timestamp":[%d, %d],
                    "indicators":{
                        "quote":[{
                            "open":[170.0, 174.0],
                            "high":[175.0, 180.0],
                            "low":[168.0, 172.0],
                            "close":[174.0, 178.0],
                            "volume":[50000000, 60000000]
                        }],
                        "adjclose":[{"adjclose":[173.5, 177.5]}]
                    }
                }]}}
                """,
                ts1, ts2);
    }
}
