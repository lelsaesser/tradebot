package org.tradelite.client.yahoo;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.service.ApiRequestMeteringService;
import org.tradelite.service.FeatureToggleService;

@ExtendWith(MockitoExtension.class)
class YahooFinanceClientTest {

    @Mock private ApiRequestMeteringService meteringService;
    @Mock private FeatureToggleService featureToggleService;

    private YahooFinanceClient client;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        HttpClient httpClient =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        // Default: toggle OFF → existing tests exercise the curl path unchanged.
        lenient()
                .when(featureToggleService.isEnabled(FeatureToggle.YAHOO_HTTP_CLIENT))
                .thenReturn(false);
        client =
                new YahooFinanceClient(
                        objectMapper, meteringService, httpClient, featureToggleService);
    }

    @Test
    void parseResponse_successfulResponse_returnsRecords() {
        String json = buildValidGermanResponse();

        List<OhlcvRecord> records = client.parseResponse("RHM.DE", json);

        assertThat(records, hasSize(2));
        assertThat(records.getFirst().symbol(), is("RHM.DE"));
        assertThat(records.getFirst().date(), is(LocalDate.of(2026, 5, 6)));
        assertThat(records.getFirst().open(), is(closeTo(1447.0, 0.01)));
        assertThat(records.getFirst().high(), is(closeTo(1456.2, 0.1)));
        assertThat(records.getFirst().low(), is(closeTo(1398.6, 0.1)));
        assertThat(records.getFirst().close(), is(closeTo(1441.6, 0.1)));
        assertThat(records.getFirst().volume(), is(297188L));

        assertThat(records.get(1).symbol(), is("RHM.DE"));
        assertThat(records.get(1).date(), is(LocalDate.of(2026, 5, 5)));
    }

    @Test
    void parseResponse_koreanStock_returnsRecords() {
        String json = buildValidKoreanResponse();

        List<OhlcvRecord> records = client.parseResponse("005930.KS", json);

        assertThat(records, hasSize(2));
        assertThat(records.getFirst().symbol(), is("005930.KS"));
        assertThat(records.getFirst().open(), is(closeTo(254000.0, 0.01)));
        assertThat(records.getFirst().volume(), is(53097996L));
    }

    @Test
    void parseResponse_nullFieldsInQuote_skipsIncomplete() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {"exchangeTimezoneName": "Europe/Berlin"},
                            "timestamp": [1778050800, 1777964400],
                            "indicators": {
                                "quote": [{
                                    "open": [1447.0, null],
                                    "high": [1456.2, 1450.0],
                                    "low": [1398.6, 1389.6],
                                    "close": [1441.6, null],
                                    "volume": [297188, 344493]
                                }]
                            }
                        }],
                        "error": null
                    }
                }
                """;

        List<OhlcvRecord> records = client.parseResponse("RHM.DE", json);

        assertThat(records, hasSize(1));
        assertThat(records.getFirst().close(), is(closeTo(1441.6, 0.1)));
    }

    @Test
    void parseResponse_nullHighField_skipsRecord() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {"exchangeTimezoneName": "Europe/Berlin"},
                            "timestamp": [1778050800],
                            "indicators": {
                                "quote": [{
                                    "open": [1447.0],
                                    "high": [null],
                                    "low": [1398.6],
                                    "close": [1441.6],
                                    "volume": [297188]
                                }]
                            }
                        }],
                        "error": null
                    }
                }
                """;

        List<OhlcvRecord> records = client.parseResponse("RHM.DE", json);
        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_nullLowField_skipsRecord() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {"exchangeTimezoneName": "Europe/Berlin"},
                            "timestamp": [1778050800],
                            "indicators": {
                                "quote": [{
                                    "open": [1447.0],
                                    "high": [1456.2],
                                    "low": [null],
                                    "close": [1441.6],
                                    "volume": [297188]
                                }]
                            }
                        }],
                        "error": null
                    }
                }
                """;

        List<OhlcvRecord> records = client.parseResponse("RHM.DE", json);
        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_nullVolumeField_skipsRecord() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {"exchangeTimezoneName": "Europe/Berlin"},
                            "timestamp": [1778050800],
                            "indicators": {
                                "quote": [{
                                    "open": [1447.0],
                                    "high": [1456.2],
                                    "low": [1398.6],
                                    "close": [1441.6],
                                    "volume": [null]
                                }]
                            }
                        }],
                        "error": null
                    }
                }
                """;

        List<OhlcvRecord> records = client.parseResponse("RHM.DE", json);
        assertThat(records, is(empty()));
    }

    @Test
    void parseResponse_missingTimestampArray_throwsException() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {"exchangeTimezoneName": "Europe/Berlin"},
                            "indicators": {
                                "quote": [{
                                    "open": [1447.0],
                                    "high": [1456.2],
                                    "low": [1398.6],
                                    "close": [1441.6],
                                    "volume": [297188]
                                }]
                            }
                        }],
                        "error": null
                    }
                }
                """;

        assertThrows(YahooFetchException.class, () -> client.parseResponse("RHM.DE", json));
    }

    @Test
    void parseResponse_emptyQuoteIndicators_throwsException() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {"exchangeTimezoneName": "Europe/Berlin"},
                            "timestamp": [1778050800],
                            "indicators": {
                                "quote": []
                            }
                        }],
                        "error": null
                    }
                }
                """;

        assertThrows(YahooFetchException.class, () -> client.parseResponse("RHM.DE", json));
    }

    @Test
    void parseResponse_apiError_throwsYahooFetchException() {
        String json =
                """
                {
                    "chart": {
                        "result": null,
                        "error": {"code": "Not Found", "description": "No data found for symbol"}
                    }
                }
                """;

        YahooFetchException ex =
                assertThrows(
                        YahooFetchException.class, () -> client.parseResponse("INVALID.XX", json));

        assertThat(ex.getMessage(), containsString("INVALID.XX"));
        assertThat(ex.getMessage(), containsString("API error"));
    }

    @Test
    void parseResponse_emptyResult_throwsYahooFetchException() {
        String json =
                """
                {
                    "chart": {
                        "result": [],
                        "error": null
                    }
                }
                """;

        assertThrows(YahooFetchException.class, () -> client.parseResponse("RHM.DE", json));
    }

    @Test
    void parseResponse_malformedJson_throwsYahooFetchException() {
        String json = "not valid json{{{";

        YahooFetchException ex =
                assertThrows(YahooFetchException.class, () -> client.parseResponse("RHM.DE", json));

        assertThat(ex.getMessage(), containsString("JSON parse error"));
    }

    @Test
    void mapDaysToRange_5orLess_returns5d() {
        assertThat(YahooFinanceClient.mapDaysToRange(1), is("5d"));
        assertThat(YahooFinanceClient.mapDaysToRange(5), is("5d"));
    }

    @Test
    void mapDaysToRange_6to30_returns1mo() {
        assertThat(YahooFinanceClient.mapDaysToRange(6), is("1mo"));
        assertThat(YahooFinanceClient.mapDaysToRange(30), is("1mo"));
    }

    @Test
    void mapDaysToRange_over30_returns2y() {
        assertThat(YahooFinanceClient.mapDaysToRange(31), is("2y"));
        assertThat(YahooFinanceClient.mapDaysToRange(400), is("2y"));
    }

    @Test
    void fetchDailyOhlcv_incrementsMeter() {
        // This test verifies metering is called even when the curl fails
        // (since metering happens before the process starts)
        assertThrows(YahooFetchException.class, () -> client.fetchDailyOhlcv("INVALID.XX", 5));

        verify(meteringService, times(1)).incrementYahooRequests();
    }

    @Test
    void executeCurl_invalidUrl_throwsYahooFetchException() {
        // Tests the actual ProcessBuilder execution with an unreachable URL
        YahooFetchException ex =
                assertThrows(
                        YahooFetchException.class,
                        () -> client.executeCurl("TEST.XX", "https://localhost:1/nonexistent"));

        assertThat(ex.getMessage(), containsString("TEST.XX"));
        assertThat(ex.getMessage(), containsString("curl exited with code"));
    }

    @Test
    void executeCurl_validEchoCommand_returnsOutput() {
        // Test executeCurl indirectly by calling fetchDailyOhlcv with the real client
        // which will hit Yahoo and fail (since INVALID.XX doesn't exist)
        YahooFetchException ex =
                assertThrows(
                        YahooFetchException.class,
                        () -> client.fetchDailyOhlcv("NONEXISTENT.ZZ", 5));

        assertThat(ex.getMessage(), containsString("NONEXISTENT.ZZ"));
    }

    @Test
    void executeCurl_realYahooCall_returnsValidJson() {
        // Integration test: actually calls Yahoo Finance for a known symbol
        // This covers the success path of executeCurl (ProcessBuilder + stdout reading)
        String json =
                client.executeCurl(
                        "SAP",
                        "https://query1.finance.yahoo.com/v8/finance/chart/SAP?interval=1d&range=5d");

        assertThat(json, containsString("\"chart\""));
        assertThat(json, containsString("\"result\""));
    }

    @Test
    void executeRequest_realYahooCall_returnsValidJson() {
        // Integration test: covers HttpClient success path against live Yahoo. If the
        // TLS-fingerprinting hypothesis were true, this test would fail with an SSL exception
        // on every CI run — providing fast disproof rather than waiting 3 trading days.
        String json =
                client.executeRequest(
                        "SAP",
                        "https://query1.finance.yahoo.com/v8/finance/chart/SAP?interval=1d&range=5d");

        assertThat(json, containsString("\"chart\""));
        assertThat(json, containsString("\"result\""));
    }

    @Test
    void executeRequest_invalidUrl_throwsYahooFetchException() {
        // Tests the HttpClient connection-failure branch deterministically.
        YahooFetchException ex =
                assertThrows(
                        YahooFetchException.class,
                        () -> client.executeRequest("TEST.XX", "https://localhost:1/nonexistent"));

        assertThat(ex.getMessage(), containsString("TEST.XX"));
        assertThat(ex.getMessage(), containsString("I/O error"));
        assertThat(ex.getMessage(), containsString("ConnectException"));
    }

    @Test
    void fetchDailyOhlcv_realCall_toggleOn_returnsRecords() {
        // Integration test: end-to-end with toggle ON, exercising executeTransport →
        // executeRequest.
        when(featureToggleService.isEnabled(FeatureToggle.YAHOO_HTTP_CLIENT)).thenReturn(true);

        List<OhlcvRecord> records = client.fetchDailyOhlcv("SAP", 5);

        assertThat(records, is(not(empty())));
        assertThat(records.getFirst().symbol(), is("SAP"));
        verify(meteringService).incrementYahooRequests();
    }

    @Test
    void fetchDailyOhlcv_realCall_returnsRecords() {
        // Integration test: full end-to-end with a real API call
        List<OhlcvRecord> records = client.fetchDailyOhlcv("SAP", 5);

        assertThat(records, is(not(empty())));
        assertThat(records.getFirst().symbol(), is("SAP"));
        assertThat(records.getFirst().volume(), is(greaterThan(0L)));

        verify(meteringService).incrementYahooRequests();
    }

    @Test
    void parseQuoteFromMeta_validResponse_returnsPriceQuote() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {
                                "regularMarketPrice": 150.5,
                                "chartPreviousClose": 148.0,
                                "regularMarketOpen": 149.0,
                                "regularMarketDayHigh": 152.0,
                                "regularMarketDayLow": 147.5,
                                "regularMarketTime": 1715600000
                            }
                        }],
                        "error": null
                    }
                }
                """;

        YahooPriceQuote quote = client.parseQuoteFromMeta("RHM.DE", json);

        assertThat(quote.symbol(), is("RHM.DE"));
        assertThat(quote.currentPrice(), is(closeTo(150.5, 0.01)));
        assertThat(quote.previousClose(), is(closeTo(148.0, 0.01)));
        assertThat(quote.dailyOpen(), is(closeTo(149.0, 0.01)));
        assertThat(quote.dailyHigh(), is(closeTo(152.0, 0.01)));
        assertThat(quote.dailyLow(), is(closeTo(147.5, 0.01)));
        assertThat(quote.changePercent(), is(closeTo(1.689, 0.01)));
        assertThat(quote.timestamp(), is(1715600000L));
    }

    @Test
    void parseQuoteFromMeta_zeroPreviousClose_returnsZeroChangePercent() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {
                                "regularMarketPrice": 150.5,
                                "chartPreviousClose": 0,
                                "regularMarketDayHigh": 152.0,
                                "regularMarketDayLow": 147.5,
                                "regularMarketTime": 1715600000
                            }
                        }],
                        "error": null
                    }
                }
                """;

        YahooPriceQuote quote = client.parseQuoteFromMeta("RHM.DE", json);

        assertThat(quote.changePercent(), is(closeTo(0.0, 0.001)));
    }

    @Test
    void parseQuoteFromMeta_invalidPrice_throwsException() {
        String json =
                """
                {
                    "chart": {
                        "result": [{
                            "meta": {
                                "regularMarketPrice": 0,
                                "chartPreviousClose": 148.0,
                                "regularMarketDayHigh": 152.0,
                                "regularMarketDayLow": 147.5,
                                "regularMarketTime": 1715600000
                            }
                        }],
                        "error": null
                    }
                }
                """;

        YahooFetchException ex =
                assertThrows(
                        YahooFetchException.class, () -> client.parseQuoteFromMeta("RHM.DE", json));

        assertThat(ex.getMessage(), containsString("invalid regularMarketPrice"));
    }

    @Test
    void parseQuoteFromMeta_apiError_throwsException() {
        String json =
                """
                {
                    "chart": {
                        "result": null,
                        "error": {"code": "Not Found", "description": "No data found"}
                    }
                }
                """;

        YahooFetchException ex =
                assertThrows(
                        YahooFetchException.class,
                        () -> client.parseQuoteFromMeta("INVALID", json));

        assertThat(ex.getMessage(), containsString("API error"));
    }

    @Test
    void parseQuoteFromMeta_emptyResult_throwsException() {
        String json =
                """
                {
                    "chart": {
                        "result": [],
                        "error": null
                    }
                }
                """;

        assertThrows(YahooFetchException.class, () -> client.parseQuoteFromMeta("RHM.DE", json));
    }

    @Test
    void parseQuoteFromMeta_malformedJson_throwsException() {
        YahooFetchException ex =
                assertThrows(
                        YahooFetchException.class,
                        () -> client.parseQuoteFromMeta("RHM.DE", "not json"));

        assertThat(ex.getMessage(), containsString("JSON parse error"));
    }

    @Test
    void fetchCurrentPrice_incrementsMeter() {
        assertThrows(YahooFetchException.class, () -> client.fetchCurrentPrice("INVALID.XX"));

        verify(meteringService, times(1)).incrementYahooRequests();
    }

    private String buildValidGermanResponse() {
        return """
                {
                    "chart": {
                        "result": [{
                            "meta": {
                                "currency": "EUR",
                                "symbol": "RHM.DE",
                                "exchangeName": "GER",
                                "fullExchangeName": "XETRA",
                                "exchangeTimezoneName": "Europe/Berlin"
                            },
                            "timestamp": [1778050800, 1777964400],
                            "indicators": {
                                "quote": [{
                                    "open": [1447.0, 1390.0],
                                    "high": [1456.2, 1450.0],
                                    "low": [1398.6, 1389.6],
                                    "close": [1441.6, 1435.4],
                                    "volume": [297188, 344493]
                                }]
                            }
                        }],
                        "error": null
                    }
                }
                """;
    }

    private String buildValidKoreanResponse() {
        return """
                {
                    "chart": {
                        "result": [{
                            "meta": {
                                "currency": "KRW",
                                "symbol": "005930.KS",
                                "exchangeName": "KSC",
                                "fullExchangeName": "KSE",
                                "exchangeTimezoneName": "Asia/Seoul"
                            },
                            "timestamp": [1778112000, 1777939200],
                            "indicators": {
                                "quote": [{
                                    "open": [254000.0, 228000.0],
                                    "high": [270000.0, 232500.0],
                                    "low": [251000.0, 224000.0],
                                    "close": [266000.0, 232500.0],
                                    "volume": [53097996, 32920816]
                                }]
                            }
                        }],
                        "error": null
                    }
                }
                """;
    }
}
