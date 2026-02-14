package org.tradelite;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class DevJobControllerTest {

    @Mock private Scheduler scheduler;

    private DevJobController controller;

    @BeforeEach
    void setUp() {
        controller = new DevJobController(scheduler);
    }

    @Test
    void stockMonitoring_callsManualStockJob() {
        ResponseEntity<Map<String, String>> response = controller.stockMonitoring();
        verify(scheduler, times(1)).manualStockMarketMonitoring();
        assertThat(response.getBody().get("status"), is("ok"));
    }

    @Test
    void cryptoMonitoring_callsJob() {
        ResponseEntity<Map<String, String>> response = controller.cryptoMonitoring();
        verify(scheduler, times(1)).cryptoMarketMonitoring();
        assertThat(response.getBody().get("job"), is("crypto-monitoring"));
    }

    @Test
    void rsiStock_callsJob() {
        ResponseEntity<Map<String, String>> response = controller.rsiStock();
        verify(scheduler, times(1)).rsiStockMonitoring();
        assertThat(response.getBody().get("job"), is("rsi-stock"));
    }

    @Test
    void rsiCrypto_callsJob() {
        ResponseEntity<Map<String, String>> response = controller.rsiCrypto();
        verify(scheduler, times(1)).rsiCryptoMonitoring();
        assertThat(response.getBody().get("job"), is("rsi-crypto"));
    }

    @Test
    void insiderReport_callsJob() {
        ResponseEntity<Map<String, String>> response = controller.insiderReport();
        verify(scheduler, times(1)).weeklyInsiderTradingReport();
        assertThat(response.getBody().get("job"), is("insider-report"));
    }

    @Test
    void sectorRotation_callsJob() {
        ResponseEntity<Map<String, String>> response = controller.sectorRotation();
        verify(scheduler, times(1)).dailySectorRotationTracking();
        assertThat(response.getBody().get("job"), is("sector-rotation"));
    }

    @Test
    void monthlyApiUsage_callsJob() {
        ResponseEntity<Map<String, String>> response = controller.monthlyApiUsage();
        verify(scheduler, times(1)).monthlyApiUsageReport();
        assertThat(response.getBody().get("job"), is("monthly-api-usage"));
    }
}
