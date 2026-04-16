package org.tradelite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DevJobControllerTest {

    @Mock private Scheduler scheduler;
    @Mock private DevDataSeeder devDataSeeder;
    @Mock private RootErrorHandler rootErrorHandler;

    private DevJobController controller;

    @BeforeEach
    void setUp() {
        controller = new DevJobController(scheduler, devDataSeeder, rootErrorHandler);
    }

    @Test
    void stockMonitoring_callsManualStockJob() {
        when(scheduler.manualStockMarketMonitoring()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.stockMonitoring();

        verify(scheduler, times(1)).manualStockMarketMonitoring();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("status"), is("ok"));
    }

    @Test
    void hourlySignals_callsManualHourlyJob() {
        when(scheduler.manualHourlySignalMonitoring()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.hourlySignals();

        verify(scheduler, times(1)).manualHourlySignalMonitoring();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("hourly-signals"));
    }

    @Test
    void cryptoMonitoring_callsJob() {
        when(scheduler.manualCryptoMarketMonitoring()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.cryptoMonitoring();

        verify(scheduler, times(1)).manualCryptoMarketMonitoring();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("crypto-monitoring"));
    }

    @Test
    void rsMonitoring_callsJob() {
        when(scheduler.manualRelativeStrengthMonitoring()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.rsMonitoring();

        verify(scheduler, times(1)).manualRelativeStrengthMonitoring();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("rs-monitoring"));
    }

    @Test
    void insiderReport_callsJob() {
        when(scheduler.manualWeeklyInsiderTradingReport()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.insiderReport();

        verify(scheduler, times(1)).manualWeeklyInsiderTradingReport();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("insider-report"));
    }

    @Test
    void sectorRotation_callsJob() {
        when(scheduler.manualDailySectorRotationTracking()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.sectorRotation();

        verify(scheduler, times(1)).manualDailySectorRotationTracking();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("sector-rotation"));
    }

    @Test
    void sectorRelativeStrengthSummary_callsJob() {
        when(scheduler.manualDailySectorRelativeStrengthReport()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.sectorRelativeStrengthSummary();

        verify(scheduler, times(1)).manualDailySectorRelativeStrengthReport();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("sector-rs-summary"));
    }

    @Test
    void tailRisk_callsJob() {
        when(scheduler.manualDailyTailRiskMonitoring()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.tailRisk();

        verify(scheduler, times(1)).manualDailyTailRiskMonitoring();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("tail-risk"));
    }

    @Test
    void bollingerReport_callsJob() {
        when(scheduler.manualDailyBollingerBandReport()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.bollingerReport();

        verify(scheduler, times(1)).manualDailyBollingerBandReport();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("bollinger-report"));
    }

    @Test
    void monthlyApiUsage_callsJob() {
        when(scheduler.manualMonthlyApiUsageReport()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.monthlyApiUsage();

        verify(scheduler, times(1)).manualMonthlyApiUsageReport();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("monthly-api-usage"));
    }

    @Test
    void seedAnalytics_callsSeeder() {
        when(rootErrorHandler.runWithStatus(any()))
                .thenAnswer(
                        invocation -> {
                            ThrowingRunnable runnable = invocation.getArgument(0);
                            runnable.run();
                            return true;
                        });

        ResponseEntity<Map<String, String>> response = controller.seedAnalytics();

        verify(devDataSeeder, times(1)).reseed();
        verify(rootErrorHandler, times(1)).runWithStatus(any());
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("seed-analytics"));
    }

    @Test
    void stockMonitoring_returnsServerErrorWhenManualJobFails() {
        when(scheduler.manualStockMarketMonitoring()).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.stockMonitoring();

        assertThat(response.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getBody().get("status"), is("error"));
        assertThat(response.getBody().get("message"), is("check logs"));
    }

    @Test
    void ohlcvFetch_callsManualOhlcvJob() {
        when(scheduler.manualOhlcvFetch()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.ohlcvFetch();

        verify(scheduler, times(1)).manualOhlcvFetch();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("status"), is("ok"));
        assertThat(response.getBody().get("job"), is("ohlcv-fetch"));
    }

    @Test
    void ohlcvFetch_returnsServerErrorWhenJobFails() {
        when(scheduler.manualOhlcvFetch()).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.ohlcvFetch();

        assertThat(response.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getBody().get("status"), is("error"));
        assertThat(response.getBody().get("job"), is("ohlcv-fetch"));
    }

    @Test
    void vfiReport_callsManualVfiJob() {
        when(scheduler.manualVfiReport()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.vfiReport();

        verify(scheduler, times(1)).manualVfiReport();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("status"), is("ok"));
        assertThat(response.getBody().get("job"), is("vfi-report"));
    }

    @Test
    void vfiReport_returnsServerErrorWhenJobFails() {
        when(scheduler.manualVfiReport()).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.vfiReport();

        assertThat(response.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getBody().get("status"), is("error"));
        assertThat(response.getBody().get("job"), is("vfi-report"));
    }
}
