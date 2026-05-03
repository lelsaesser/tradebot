package org.tradelite;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev")
@RequestMapping("/dev/jobs")
public class DevJobController {

    private static final int SMOKE_TEST_OHLCV_SYMBOLS = 3;

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

    @PostMapping("/rs-monitoring")
    public ResponseEntity<Map<String, String>> rsMonitoring() {
        return runJob("rs-monitoring", scheduler::manualRelativeStrengthMonitoring);
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
        return runJob("sector-rs-summary", scheduler::manualDailySectorRelativeStrengthReport);
    }

    @PostMapping("/tail-risk")
    public ResponseEntity<Map<String, String>> tailRisk() {
        return runJob("tail-risk", scheduler::manualDailyTailRiskMonitoring);
    }

    @PostMapping("/ema-report")
    public ResponseEntity<Map<String, String>> emaReport() {
        return runJob("ema-report", scheduler::manualEmaReport);
    }

    @PostMapping("/monthly-api-usage")
    public ResponseEntity<Map<String, String>> monthlyApiUsage() {
        return runJob("monthly-api-usage", scheduler::manualMonthlyApiUsageReport);
    }

    @PostMapping("/seed-analytics")
    public ResponseEntity<Map<String, String>> seedAnalytics() {
        return runJob(
                "seed-analytics", () -> rootErrorHandler.runWithStatus(devDataSeeder::reseed));
    }

    @PostMapping("/ohlcv-fetch")
    public ResponseEntity<Map<String, String>> ohlcvFetch() {
        return runJob("ohlcv-fetch", scheduler::manualOhlcvFetch);
    }

    @PostMapping("/vfi-report")
    public ResponseEntity<Map<String, String>> vfiReport() {
        return runJob("vfi-report", scheduler::manualVfiReport);
    }

    @PostMapping("/pullback-buy-alert")
    public ResponseEntity<Map<String, String>> pullbackBuyAlert() {
        return runJob("pullback-buy-alert", scheduler::manualPullbackBuyAlert);
    }

    @PostMapping("/earnings-calendar")
    public ResponseEntity<Map<String, String>> earningsCalendar() {
        return runJob("earnings-calendar", scheduler::manualEarningsCalendarCheck);
    }

    @PostMapping("/run-all")
    public ResponseEntity<Map<String, Object>> runAll() {
        LinkedHashMap<String, String> results = new LinkedHashMap<>();
        int failures = 0;

        // Phase 1: seed data
        failures +=
                runAndRecord(
                        results,
                        "seed-analytics",
                        () -> rootErrorHandler.runWithStatus(devDataSeeder::reseed));

        // Phase 2: fetch OHLCV for a small subset (needed by VFI)
        failures +=
                runAndRecord(
                        results,
                        "ohlcv-fetch",
                        () -> scheduler.manualOhlcvFetchLimited(SMOKE_TEST_OHLCV_SYMBOLS));

        // Phase 3: all independent jobs
        failures +=
                runAndRecord(results, "stock-monitoring", scheduler::manualStockMarketMonitoring);
        failures +=
                runAndRecord(results, "hourly-signals", scheduler::manualHourlySignalMonitoring);
        failures +=
                runAndRecord(results, "crypto-monitoring", scheduler::manualCryptoMarketMonitoring);
        failures +=
                runAndRecord(results, "rs-monitoring", scheduler::manualRelativeStrengthMonitoring);
        failures +=
                runAndRecord(
                        results, "insider-report", scheduler::manualWeeklyInsiderTradingReport);
        failures +=
                runAndRecord(
                        results, "sector-rotation", scheduler::manualDailySectorRotationTracking);
        failures +=
                runAndRecord(
                        results,
                        "sector-rs-summary",
                        scheduler::manualDailySectorRelativeStrengthReport);
        failures += runAndRecord(results, "tail-risk", scheduler::manualDailyTailRiskMonitoring);
        failures += runAndRecord(results, "ema-report", scheduler::manualEmaReport);
        failures +=
                runAndRecord(results, "monthly-api-usage", scheduler::manualMonthlyApiUsageReport);
        failures += runAndRecord(results, "pullback-buy-alert", scheduler::manualPullbackBuyAlert);
        failures +=
                runAndRecord(results, "earnings-calendar", scheduler::manualEarningsCalendarCheck);

        // Phase 4: VFI last (depends on OHLCV data)
        failures += runAndRecord(results, "vfi-report", scheduler::manualVfiReport);

        String status;
        if (failures == 0) {
            status = "ok";
        } else if (failures == results.size()) {
            status = "error";
        } else {
            status = "partial";
        }

        LinkedHashMap<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("total", results.size());
        body.put("passed", results.size() - failures);
        body.put("failed", failures);
        body.put("results", results);

        if (failures == 0) {
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.status(207).body(body);
    }

    private int runAndRecord(
            LinkedHashMap<String, String> results, String job, BooleanSupplier runner) {
        boolean ok = runner.getAsBoolean();
        results.put(job, ok ? "ok" : "error");
        return ok ? 0 : 1;
    }

    private ResponseEntity<Map<String, String>> runJob(String job, BooleanSupplier jobRunner) {
        if (jobRunner.getAsBoolean()) {
            return ResponseEntity.ok(Map.of("status", "ok", "job", job));
        }
        return ResponseEntity.internalServerError()
                .body(Map.of("status", "error", "job", job, "message", "check logs"));
    }
}
