package org.tradelite;

import java.util.function.BooleanSupplier;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@RequestMapping("/dev/jobs")
public class DevJobController {

    private final Scheduler scheduler;
    private final DevDataSeeder devDataSeeder;
    private final RootErrorHandler rootErrorHandler;

    public DevJobController(
            Scheduler scheduler, DevDataSeeder devDataSeeder, RootErrorHandler rootErrorHandler) {
        this.scheduler = scheduler;
        this.devDataSeeder = devDataSeeder;
        this.rootErrorHandler = rootErrorHandler;
    }

    @PostMapping("/stock-monitoring")
    public ResponseEntity<Map<String, String>> stockMonitoring() {
        return runJob("stock-monitoring", scheduler::manualStockMarketMonitoring);
    }

    @PostMapping("/hourly-signals")
    public ResponseEntity<Map<String, String>> hourlySignals() {
        return runJob("hourly-signals", scheduler::manualHourlySignalMonitoring);
    }

    @PostMapping("/crypto-monitoring")
    public ResponseEntity<Map<String, String>> cryptoMonitoring() {
        return runJob("crypto-monitoring", scheduler::manualCryptoMarketMonitoring);
    }

    @PostMapping("/rsi-stock")
    public ResponseEntity<Map<String, String>> rsiStock() {
        return runJob("rsi-stock", scheduler::manualRsiStockMonitoring);
    }

    @PostMapping("/rsi-crypto")
    public ResponseEntity<Map<String, String>> rsiCrypto() {
        return runJob("rsi-crypto", scheduler::manualRsiCryptoMonitoring);
    }

    @PostMapping("/insider-report")
    public ResponseEntity<Map<String, String>> insiderReport() {
        return runJob("insider-report", scheduler::manualWeeklyInsiderTradingReport);
    }

    @PostMapping("/sector-rotation")
    public ResponseEntity<Map<String, String>> sectorRotation() {
        return runJob("sector-rotation", scheduler::manualDailySectorRotationTracking);
    }

    @PostMapping("/sector-rs-summary")
    public ResponseEntity<Map<String, String>> sectorRelativeStrengthSummary() {
        return runJob(
                "sector-rs-summary", scheduler::manualDailySectorRelativeStrengthReport);
    }

    @PostMapping("/tail-risk")
    public ResponseEntity<Map<String, String>> tailRisk() {
        return runJob("tail-risk", scheduler::manualDailyTailRiskMonitoring);
    }

    @PostMapping("/bollinger-report")
    public ResponseEntity<Map<String, String>> bollingerReport() {
        return runJob("bollinger-report", scheduler::manualDailyBollingerBandReport);
    }

    @PostMapping("/monthly-api-usage")
    public ResponseEntity<Map<String, String>> monthlyApiUsage() {
        return runJob("monthly-api-usage", scheduler::manualMonthlyApiUsageReport);
    }

    @PostMapping("/seed-analytics")
    public ResponseEntity<Map<String, String>> seedAnalytics() {
        return runJob("seed-analytics", () -> rootErrorHandler.runWithStatus(devDataSeeder::reseed));
    }

    private ResponseEntity<Map<String, String>> runJob(String job, BooleanSupplier jobRunner) {
        if (jobRunner.getAsBoolean()) {
            return ResponseEntity.ok(Map.of("status", "ok", "job", job));
        }
        return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "job", job, "message", "check logs"));
    }
}
