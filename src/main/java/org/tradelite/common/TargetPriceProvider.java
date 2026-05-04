package org.tradelite.common;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.tradelite.core.IgnoreReason;
import org.tradelite.repository.SqliteIgnoredSymbolRepository;
import org.tradelite.repository.SqliteIgnoredSymbolRepository.IgnoredSymbolRow;
import org.tradelite.repository.TargetPriceRepository;

@Slf4j
@Component
public class TargetPriceProvider {

    public static final long IGNORE_DURATION_TTL_SECONDS = 3600L * 12; // 12 hours

    private final TargetPriceRepository targetPriceRepository;
    private final SqliteIgnoredSymbolRepository ignoredSymbolRepository;

    @Autowired
    public TargetPriceProvider(
            TargetPriceRepository targetPriceRepository,
            SqliteIgnoredSymbolRepository ignoredSymbolRepository) {
        this.targetPriceRepository = targetPriceRepository;
        this.ignoredSymbolRepository = ignoredSymbolRepository;
    }

    public List<TargetPrice> getStockTargetPrices() {
        return targetPriceRepository.findByAssetType(AssetType.STOCK);
    }

    public List<TargetPrice> getCoinTargetPrices() {
        return targetPriceRepository.findByAssetType(AssetType.COIN);
    }

    public void addIgnoredSymbol(TickerSymbol symbol, IgnoreReason reason, int alertThreshold) {
        ignoredSymbolRepository.save(
                symbol.getName(),
                reason.getReason(),
                Instant.now().getEpochSecond(),
                alertThreshold == 0 ? null : alertThreshold);
    }

    public void addIgnoredSymbol(TickerSymbol symbol, IgnoreReason reason) {
        addIgnoredSymbol(symbol, reason, 0);
    }

    public boolean isSymbolIgnored(TickerSymbol symbol, IgnoreReason reason, int alertThreshold) {
        Optional<IgnoredSymbolRow> row =
                ignoredSymbolRepository.findBySymbolAndReason(symbol.getName(), reason.getReason());

        if (row.isEmpty()) {
            return false;
        }

        if (alertThreshold > 0) {
            Integer lastAlertedThreshold = row.get().alertThreshold();
            return lastAlertedThreshold != null && alertThreshold <= lastAlertedThreshold;
        }

        long elapsedSeconds =
                Duration.between(Instant.ofEpochSecond(row.get().ignoredAt()), Instant.now())
                        .getSeconds();
        if (elapsedSeconds < reason.getTtlSeconds()) {
            log.info(
                    "{} is ignored for reason {}. Time remaining: {} seconds",
                    symbol.getName(),
                    reason,
                    reason.getTtlSeconds() - elapsedSeconds);
            return true;
        }
        return false;
    }

    public boolean isSymbolIgnored(TickerSymbol symbol, IgnoreReason reason) {
        return isSymbolIgnored(symbol, reason, 0);
    }

    public void cleanupIgnoreSymbols(long ttlSeconds) {
        long cutoff = Instant.now().getEpochSecond() - ttlSeconds;
        ignoredSymbolRepository.deleteExpiredEntries(cutoff);
    }

    public void updateTargetPrice(
            TickerSymbol symbol, Double newBuyTarget, Double newSellTarget, AssetType type) {
        List<TargetPrice> prices = targetPriceRepository.findByAssetType(type);

        for (TargetPrice tp : prices) {
            if (tp.getSymbol().equalsIgnoreCase(symbol.getName())) {
                if (newBuyTarget != null) {
                    tp.setBuyTarget(newBuyTarget);
                }
                if (newSellTarget != null) {
                    tp.setSellTarget(newSellTarget);
                }
                targetPriceRepository.save(tp, type);
                return;
            }
        }
    }

    public boolean addTargetPrice(TargetPrice targetPrice, AssetType type) {
        List<TargetPrice> existing = targetPriceRepository.findByAssetType(type);

        boolean alreadyExists =
                existing.stream()
                        .anyMatch(tp -> tp.getSymbol().equalsIgnoreCase(targetPrice.getSymbol()));

        if (alreadyExists) {
            log.warn("Symbol {} already exists in target prices", targetPrice.getSymbol());
            return false;
        }

        targetPriceRepository.save(targetPrice, type);
        return true;
    }

    public boolean removeSymbolFromTargetPrices(String ticker, AssetType type) {
        return targetPriceRepository.deleteBySymbolAndType(ticker, type);
    }
}
