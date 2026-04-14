package org.tradelite.quant;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.SectorEtfRegistry;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RelativeStrengthService.RsResult;
import org.tradelite.service.StockSymbolRegistry;

@Slf4j
@Component
@RequiredArgsConstructor
public class VfiTracker {

    private final VfiService vfiService;
    private final RelativeStrengthService relativeStrengthService;
    private final TelegramGateway telegramClient;
    private final StockSymbolRegistry stockSymbolRegistry;
    private final FeatureToggleService featureToggleService;

    public void sendDailyReport() {
        if (!featureToggleService.isEnabled(FeatureToggle.VFI_REPORT)) {
            log.debug("VFI report feature toggle is disabled, skipping");
            return;
        }

        List<SymbolResult> results = analyzeAllSymbols();

        if (results.isEmpty()) {
            log.warn("No combined RS+VFI data available for any symbol");
            return;
        }

        String report = buildReport(results);
        telegramClient.sendMessage(report);
        log.info("Daily RS+VFI report sent: {} symbols analyzed", results.size());
    }

    List<SymbolResult> analyzeAllSymbols() {
        List<SymbolResult> results = new ArrayList<>();

        for (Map.Entry<String, String> entry : SectorEtfRegistry.allEtfs().entrySet()) {
            analyzeSymbol(entry.getKey(), entry.getValue()).ifPresent(results::add);
        }

        for (StockSymbol stock : stockSymbolRegistry.getAll()) {
            if (stockSymbolRegistry.isEtf(stock.getTicker())) {
                continue;
            }
            analyzeSymbol(stock.getTicker(), stock.getCompanyName()).ifPresent(results::add);
        }

        return results;
    }

    private Optional<SymbolResult> analyzeSymbol(String symbol, String displayName) {
        Optional<VfiAnalysis> vfiOpt = vfiService.analyze(symbol, displayName);
        if (vfiOpt.isEmpty()) {
            log.debug("No VFI data for {}", symbol);
            return Optional.empty();
        }

        Optional<RsResult> rsOpt = relativeStrengthService.getCurrentRsResult(symbol);
        if (rsOpt.isEmpty()) {
            log.debug("No RS data for {}", symbol);
            return Optional.empty();
        }

        VfiAnalysis vfi = vfiOpt.get();
        RsResult rs = rsOpt.get();

        boolean rsPositive = rs.rs() > rs.ema();
        boolean vfiPositive = vfi.isVfiPositive();
        CombinedSignalType signal = CombinedSignalType.classify(rsPositive, vfiPositive);
        double rsPercent = (rs.rs() / rs.ema() - 1) * 100;

        return Optional.of(
                new SymbolResult(
                        symbol,
                        displayName,
                        signal,
                        rsPercent,
                        vfi.vfiValue(),
                        vfi.signalLineValue()));
    }

    String buildReport(List<SymbolResult> results) {
        List<SymbolResult> green =
                results.stream().filter(r -> r.signal == CombinedSignalType.GREEN).toList();
        List<SymbolResult> yellow =
                results.stream().filter(r -> r.signal == CombinedSignalType.YELLOW).toList();
        List<SymbolResult> red =
                results.stream().filter(r -> r.signal == CombinedSignalType.RED).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("*RS + VFI Combined Report*\n\n");

        if (!green.isEmpty()) {
            sb.append("\uD83D\uDFE2 *Both Positive (RS↑ + VFI↑):*\n");
            for (SymbolResult r : green) {
                sb.append(formatLine(r)).append("\n");
            }
            sb.append("\n");
        }

        if (!yellow.isEmpty()) {
            sb.append("\uD83D\uDFE1 *Mixed Signal:*\n");
            for (SymbolResult r : yellow) {
                sb.append(formatLine(r)).append("\n");
            }
            sb.append("\n");
        }

        if (!red.isEmpty()) {
            sb.append("\uD83D\uDD34 *Both Negative (RS↓ + VFI↓):*\n");
            for (SymbolResult r : red) {
                sb.append(formatLine(r)).append("\n");
            }
            sb.append("\n");
        }

        sb.append(
                String.format(
                        "_\uD83D\uDFE2 %d | \uD83D\uDFE1 %d | \uD83D\uDD34 %d_",
                        green.size(), yellow.size(), red.size()));

        return sb.toString();
    }

    private String formatLine(SymbolResult r) {
        return String.format(
                "\u2022 %s (%s) — RS %+.1f%% | VFI %+.1f / sig %+.1f",
                r.symbol, r.displayName, r.rsPercent, r.vfiValue, r.signalLineValue);
    }

    record SymbolResult(
            String symbol,
            String displayName,
            CombinedSignalType signal,
            double rsPercent,
            double vfiValue,
            double signalLineValue) {}
}
