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

    public DevJobController(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @PostMapping("/stock-monitoring")
    public ResponseEntity<Map<String, String>> stockMonitoring() {
        scheduler.manualStockMarketMonitoring();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "stock-monitoring"));
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

    @PostMapping("/monthly-api-usage")
    public ResponseEntity<Map<String, String>> monthlyApiUsage() {
        scheduler.monthlyApiUsageReport();
        return ResponseEntity.ok(Map.of("status", "ok", "job", "monthly-api-usage"));
    }
}
