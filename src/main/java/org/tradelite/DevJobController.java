package org.tradelite;

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

    public DevJobController(Scheduler scheduler, DevDataSeeder devDataSeeder) {
        this.scheduler = scheduler;
        this.devDataSeeder = devDataSeeder;
    }

    @PostMapping("/stock-monitoring")
    public ResponseEntity<Map<String, String>> stockMonitoring() {
        scheduler.manualStockMarketMonitoring();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "stock-monitoring"));
    }

    @PostMapping("/hourly-signals")
    public ResponseEntity<Map<String, String>> hourlySignals() {
        scheduler.manualHourlySignalMonitoring();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "hourly-signals"));
    }

    @PostMapping("/crypto-monitoring")
    public ResponseEntity<Map<String, String>> cryptoMonitoring() {
        scheduler.cryptoMarketMonitoring();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "crypto-monitoring"));
    }

    @PostMapping("/rsi-stock")
    public ResponseEntity<Map<String, String>> rsiStock() {
        scheduler.rsiStockMonitoring();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "rsi-stock"));
    }

    @PostMapping("/rsi-crypto")
    public ResponseEntity<Map<String, String>> rsiCrypto() {
        scheduler.rsiCryptoMonitoring();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "rsi-crypto"));
    }

    @PostMapping("/insider-report")
    public ResponseEntity<Map<String, String>> insiderReport() {
        scheduler.weeklyInsiderTradingReport();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "insider-report"));
    }

    @PostMapping("/sector-rotation")
    public ResponseEntity<Map<String, String>> sectorRotation() {
        scheduler.dailySectorRotationTracking();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "sector-rotation"));
    }

    @PostMapping("/sector-rs-summary")
    public ResponseEntity<Map<String, String>> sectorRelativeStrengthSummary() {
        scheduler.dailySectorRelativeStrengthReport();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "sector-rs-summary"));
    }

    @PostMapping("/tail-risk")
    public ResponseEntity<Map<String, String>> tailRisk() {
        scheduler.dailyTailRiskMonitoring();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "tail-risk"));
    }

    @PostMapping("/bollinger-report")
    public ResponseEntity<Map<String, String>> bollingerReport() {
        scheduler.dailyBollingerBandReport();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "bollinger-report"));
    }

    @PostMapping("/monthly-api-usage")
    public ResponseEntity<Map<String, String>> monthlyApiUsage() {
        scheduler.monthlyApiUsageReport();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "monthly-api-usage"));
    }

    @PostMapping("/seed-analytics")
    public ResponseEntity<Map<String, String>> seedAnalytics() {
        devDataSeeder.reseed();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "seed-analytics"));
    }
}
