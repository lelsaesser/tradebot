package org.tradelite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

@SuppressWarnings("DataFlowIssue")
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

    @Test
    void pullbackBuyAlert_callsManualJob() {
        when(scheduler.manualPullbackBuyAlert()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.pullbackBuyAlert();

        verify(scheduler, times(1)).manualPullbackBuyAlert();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("status"), is("ok"));
        assertThat(response.getBody().get("job"), is("pullback-buy-alert"));
    }

    @Test
    void pullbackBuyAlert_returnsServerErrorWhenJobFails() {
        when(scheduler.manualPullbackBuyAlert()).thenReturn(false);

        ResponseEntity<Map<String, String>> response = controller.pullbackBuyAlert();

        assertThat(response.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThat(response.getBody().get("status"), is("error"));
        assertThat(response.getBody().get("job"), is("pullback-buy-alert"));
    }

    @Test
    void emaReport_callsJob() {
        when(scheduler.manualEmaReport()).thenReturn(true);

        ResponseEntity<Map<String, String>> response = controller.emaReport();

        verify(scheduler, times(1)).manualEmaReport();
        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("job"), is("ema-report"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void runAll_allJobsPass_returnsOk() {
        when(rootErrorHandler.runWithStatus(any())).thenReturn(true);
        when(scheduler.manualOhlcvFetchLimited(anyInt())).thenReturn(true);
        when(scheduler.manualStockMarketMonitoring()).thenReturn(true);
        when(scheduler.manualHourlySignalMonitoring()).thenReturn(true);
        when(scheduler.manualCryptoMarketMonitoring()).thenReturn(true);
        when(scheduler.manualRelativeStrengthMonitoring()).thenReturn(true);
        when(scheduler.manualWeeklyInsiderTradingReport()).thenReturn(true);
        when(scheduler.manualDailySectorRotationTracking()).thenReturn(true);
        when(scheduler.manualDailySectorRelativeStrengthReport()).thenReturn(true);
        when(scheduler.manualDailyTailRiskMonitoring()).thenReturn(true);
        when(scheduler.manualEmaReport()).thenReturn(true);
        when(scheduler.manualMonthlyApiUsageReport()).thenReturn(true);
        when(scheduler.manualPullbackBuyAlert()).thenReturn(true);
        when(scheduler.manualVfiReport()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.runAll();

        assertThat(response.getStatusCode(), is(HttpStatus.OK));
        assertThat(response.getBody().get("status"), is("ok"));
        assertThat(response.getBody().get("failed"), is(0));
        assertThat(response.getBody().get("total"), is(14));

        Map<String, String> results = (Map<String, String>) response.getBody().get("results");
        assertThat(results.get("vfi-report"), is("ok"));
        assertThat(results.get("seed-analytics"), is("ok"));
        assertThat(results.get("pullback-buy-alert"), is("ok"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void runAll_someJobsFail_returnsPartialWith207() {
        when(rootErrorHandler.runWithStatus(any())).thenReturn(true);
        when(scheduler.manualOhlcvFetchLimited(anyInt())).thenReturn(true);
        when(scheduler.manualStockMarketMonitoring()).thenReturn(true);
        when(scheduler.manualHourlySignalMonitoring()).thenReturn(true);
        when(scheduler.manualCryptoMarketMonitoring()).thenReturn(false);
        when(scheduler.manualRelativeStrengthMonitoring()).thenReturn(true);
        when(scheduler.manualWeeklyInsiderTradingReport()).thenReturn(true);
        when(scheduler.manualDailySectorRotationTracking()).thenReturn(true);
        when(scheduler.manualDailySectorRelativeStrengthReport()).thenReturn(true);
        when(scheduler.manualDailyTailRiskMonitoring()).thenReturn(true);
        when(scheduler.manualEmaReport()).thenReturn(true);
        when(scheduler.manualMonthlyApiUsageReport()).thenReturn(true);
        when(scheduler.manualPullbackBuyAlert()).thenReturn(true);
        when(scheduler.manualVfiReport()).thenReturn(true);

        ResponseEntity<Map<String, Object>> response = controller.runAll();

        assertThat(response.getStatusCode(), is(HttpStatusCode.valueOf(207)));
        assertThat(response.getBody().get("status"), is("partial"));
        assertThat(response.getBody().get("failed"), is(1));
        assertThat(response.getBody().get("passed"), is(13));

        Map<String, String> results = (Map<String, String>) response.getBody().get("results");
        assertThat(results.get("crypto-monitoring"), is("error"));
        assertThat(results.get("stock-monitoring"), is("ok"));
    }
}
