package org.tradelite.web.dashboard;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.tradelite.common.AssetType;
import org.tradelite.common.Exchange;
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
@RequestMapping("/api/v1")
@Validated
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
        Map<String, TargetPrice> targets =
                targetPriceProvider.getStockTargetPrices().stream()
                        .collect(
                                Collectors.toMap(
                                        tp -> tp.getSymbol().toUpperCase(), Function.identity()));

        List<WatchlistRow> rows = new ArrayList<>();
        for (StockSymbol symbol : symbolRegistry.getAll()) {
            String ticker = symbol.getName().toUpperCase();
            TargetPrice tp = targets.get(ticker);
            Double buyTarget = tp != null && tp.getBuyTarget() != 0.0 ? tp.getBuyTarget() : null;
            Double sellTarget = tp != null && tp.getSellTarget() != 0.0 ? tp.getSellTarget() : null;
            rows.add(
                    new WatchlistRow(
                            ticker,
                            symbol.getDisplayName(),
                            deriveExchange(ticker),
                            prices.get(ticker),
                            buyTarget,
                            sellTarget));
        }
        return new WatchlistResponse(rows);
    }

    @PostMapping("/symbols")
    public ResponseEntity<String> addSymbol(@Valid @RequestBody AddSymbolRequest request) {
        SymbolManagementService.AddResult result =
                symbolManagementService.addSymbol(
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
    public ResponseEntity<String> setTarget(@Valid @RequestBody SetTargetRequest request) {
        var symbol = symbolRegistry.fromString(request.ticker().toUpperCase());
        if (symbol.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        targetPriceProvider.updateTargetPrice(
                symbol.get(), request.side(), request.price(), AssetType.STOCK);
        return ResponseEntity.ok().build();
    }

    /**
     * Labels a ticker by its trading venue or asset class. ETFs win first (they're an asset class
     * regardless of listing exchange). International tickers resolve via {@link
     * Exchange#fromTicker} added in #498 (XETRA / KRX / JPX / STO / PAR). Domestic stocks fall back
     * to "US" — honest about not distinguishing NYSE / NASDAQ / AMEX in the registry.
     */
    private String deriveExchange(String ticker) {
        if (symbolRegistry.isEtf(ticker)) {
            return "ETF";
        }
        return Exchange.fromTicker(ticker).map(Enum::name).orElse("US");
    }
}
