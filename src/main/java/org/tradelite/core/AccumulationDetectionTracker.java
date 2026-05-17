package org.tradelite.core;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.tradelite.client.telegram.TelegramGateway;
import org.tradelite.common.FeatureToggle;
import org.tradelite.common.StockSymbol;
import org.tradelite.common.SymbolRegistry;
import org.tradelite.quant.EmaAnalysis;
import org.tradelite.quant.EmaService;
import org.tradelite.quant.TrendDirection;
import org.tradelite.quant.VfiAnalysis;
import org.tradelite.quant.VfiService;
import org.tradelite.repository.AccumulationStreakRepository;
import org.tradelite.service.FeatureToggleService;
import org.tradelite.service.RelativeStrengthService;
import org.tradelite.service.RsTrendResult;

/**
 * Orchestrates accumulation detection across all tracked stocks and sends consolidated Telegram
 * alerts when institutional accumulation is identified.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccumulationDetectionTracker {

    private final EmaService emaService;
    private final VfiService vfiService;
    private final RelativeStrengthService relativeStrengthService;
    private final AccumulationDetectionService accumulationDetectionService;
    private final TelegramGateway telegramClient;
    private final SymbolRegistry symbolRegistry;
    private final FeatureToggleService featureToggleService;
    private final AccumulationStreakRepository accumulationStreakRepository;

    public void analyzeAndSendAlerts() {
        if (!featureToggleService.isEnabled(FeatureToggle.ACCUMULATION_DETECTION)) {
            return;
        }

        List<AccumulationSignal> signals = new ArrayList<>();

        for (StockSymbol stock : symbolRegistry.getStocks()) {
            if (RelativeStrengthService.BENCHMARK_SYMBOL.equals(stock.getTicker())) {
                continue;
            }

            Optional<EmaAnalysis> emaOpt =
                    emaService.analyze(stock.getTicker(), stock.getCompanyName());
            if (emaOpt.isEmpty()) {
                log.warn("Accumulation skip: {} — EMA analysis returned empty", stock.getTicker());
                continue;
            }

            Optional<VfiAnalysis> vfiOpt =
                    vfiService.analyze(stock.getTicker(), stock.getCompanyName());
            if (vfiOpt.isEmpty()) {
                log.warn("Accumulation skip: {} — VFI analysis returned empty", stock.getTicker());
                continue;
            }

            // RS is informational — null-safe if unavailable
            RsTrendResult rsTrend =
                    relativeStrengthService.getRsTrend(stock.getTicker()).orElse(null);

            Optional<AccumulationSignal> signal =
                    accumulationDetectionService.evaluate(
                            stock.getTicker(),
                            stock.getCompanyName(),
                            emaOpt.get(),
                            vfiOpt.get(),
                            rsTrend);

            signal.ifPresent(signals::add);
        }

        Map<String, Integer> streakDays = updateStreaks(signals);

        if (!signals.isEmpty()) {
            String message = buildAlertMessage(signals, streakDays);
            telegramClient.sendMessage(message);
            log.info("Accumulation detection alert sent for {} stock(s)", signals.size());
        }
    }

    Map<String, Integer> updateStreaks(List<AccumulationSignal> signals) {
        LocalDate today = LocalDate.now();
        Set<String> signalingSymbols = new HashSet<>();
        Map<String, Integer> streakDays = new HashMap<>();

        for (AccumulationSignal signal : signals) {
            String symbol = signal.symbol();
            signalingSymbols.add(symbol);

            Optional<AccumulationStreak> existing =
                    accumulationStreakRepository.findBySymbol(symbol);

            if (existing.isPresent() && existing.get().lastUpdated().equals(today)) {
                // Already updated today — idempotent
                streakDays.put(symbol, existing.get().streakDays());
                continue;
            }

            int newStreakDays = existing.map(streak -> streak.streakDays() + 1).orElse(1);

            accumulationStreakRepository.save(new AccumulationStreak(symbol, newStreakDays, today));
            streakDays.put(symbol, newStreakDays);
        }

        accumulationStreakRepository.deleteAllExcept(signalingSymbols);
        return streakDays;
    }

    String buildAlertMessage(List<AccumulationSignal> signals, Map<String, Integer> streakDays) {
        StringBuilder sb = new StringBuilder();
        sb.append("*Institutional accumulation detected*\n");

        for (AccumulationSignal signal : signals) {
            int streak = streakDays.getOrDefault(signal.symbol(), 1);
            sb.append("\n*")
                    .append(signal.displayName())
                    .append(" (")
                    .append(signal.symbol())
                    .append(")");
            if (streak > 1) {
                sb.append(" — ").append(streak).append(" days");
            }
            sb.append("*\n");
            sb.append(
                    String.format(
                            "  Price: $%.2f | EMA9: $%.2f < EMA21: $%.2f%n",
                            signal.currentPrice(), signal.ema9(), signal.ema21()));
            sb.append(
                    String.format(
                            "  VFI: %+.2f | Signal: %+.2f%n",
                            signal.vfiValue(), signal.vfiSignalLine()));
            sb.append(
                    String.format(
                            "  RS vs SPY: %.4f %s | EMA: %.4f %s%n",
                            signal.rsValue(),
                            trendArrow(signal.rsTrend()),
                            signal.rsEma(),
                            trendArrow(signal.rsEmaTrend())));
        }

        sb.append("\n_Based on EMA crossdown + positive rising VFI_");
        return sb.toString();
    }

    static String trendArrow(TrendDirection direction) {
        return switch (direction) {
            case RISING -> "↑";
            case FLAT -> "→";
            case FALLING -> "↓";
        };
    }
}
