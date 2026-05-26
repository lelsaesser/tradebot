package org.tradelite.web.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tradelite.common.AssetType;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.common.TargetPrice;
import org.tradelite.common.TargetPriceProvider;
import org.tradelite.service.LivePriceCache;
import org.tradelite.service.SymbolManagementService;
import org.tradelite.web.dashboard.dto.AddSymbolRequest;
import org.tradelite.web.dashboard.dto.SetTargetRequest;
import org.tradelite.web.dashboard.dto.WatchlistResponse;
import org.tradelite.web.dashboard.dto.WatchlistRow;

@RestController
@RequestMapping("/api")
public class WatchlistController {

    private final SymbolRegistry symbolRegistry;
    private final TargetPriceProvider targetPriceProvider;
    private final LivePriceCache livePriceCache;
    private final SymbolManagementService symbolManagementService;

    public WatchlistController(
            SymbolRegistry symbolRegistry,
            TargetPriceProvider targetPriceProvider,
            LivePriceCache livePriceCache,
            SymbolManagementService symbolManagementService) {
        this.symbolRegistry = symbolRegistry;
        this.targetPriceProvider = targetPriceProvider;
        this.livePriceCache = livePriceCache;
        this.symbolManagementService = symbolManagementService;
    }

    @GetMapping("/watchlist")
    public WatchlistResponse getWatchlist() {
        Map<String, Double> prices = livePriceCache.getAll();
        Map<String, TargetPrice> targets = targetPriceProvider.getStockTargetPrices().stream()
                .collect(Collectors.toMap(tp -> tp.getSymbol().toUpperCase(), Function.identity()));

        List<WatchlistRow> rows = new ArrayList<>();
        for (StockSymbol symbol : symbolRegistry.getAll()) {
            String ticker = symbol.getName().toUpperCase();
            TargetPrice tp = targets.get(ticker);
            rows.add(new WatchlistRow(
                    ticker,
                    symbol.getDisplayName(),
                    deriveExchange(ticker),
                    prices.get(ticker),
                    tp != null ? tp.getBuyTarget() : null,
                    tp != null ? tp.getSellTarget() : null));
        }
        return new WatchlistResponse(rows);
    }

    @PostMapping("/symbols")
    public ResponseEntity<String> addSymbol(@RequestBody AddSymbolRequest request) {
        if (request.ticker() == null || request.ticker().isBlank()
                || request.displayName() == null || request.displayName().isBlank()) {
            return ResponseEntity.badRequest().body("ticker and displayName are required");
        }
        SymbolManagementService.AddResult result = symbolManagementService.addSymbol(
                request.ticker().toUpperCase(), request.displayName(), null, null);
        if (!result.success()) {
            return ResponseEntity.badRequest().body(result.message());
        }
        return ResponseEntity.ok(result.message());
    }

    @DeleteMapping("/symbols/{ticker}")
    public ResponseEntity<Void> removeSymbol(@PathVariable String ticker) {
        boolean removed = symbolManagementService.removeSymbol(ticker.toUpperCase());
        return removed ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/targets")
    public ResponseEntity<String> setTarget(@RequestBody SetTargetRequest request) {
        if (request.ticker() == null || request.ticker().isBlank() || request.side() == null) {
            return ResponseEntity.badRequest().body("ticker and side are required");
        }
        var symbol = symbolRegistry.fromString(request.ticker().toUpperCase());
        if (symbol.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        targetPriceProvider.updateTargetPrice(symbol.get(), request.side(), request.price(), AssetType.STOCK);
        return ResponseEntity.ok().build();
    }

    private String deriveExchange(String ticker) {
        if (symbolRegistry.isEtf(ticker)) {
            return "ETF";
        }
        if (ticker.endsWith(".DE")) {
            return "XETRA";
        }
        if (ticker.endsWith(".KS")) {
            return "KRX";
        }
        return "NASDAQ";
    }
}
