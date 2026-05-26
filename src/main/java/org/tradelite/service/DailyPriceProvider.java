package org.tradelite.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.LivePriceCache.PricedAt;
import org.tradelite.service.model.DailyPrice;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceProvider {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    private final OhlcvRepository ohlcvRepository;
    private final PriceQuoteRepository priceQuoteRepository;
    private final LivePriceCache livePriceCache;

    public List<DailyPrice> findDailyClosingPrices(String symbol, int days) {
        List<OhlcvRecord> ohlcvRecords = ohlcvRepository.findBySymbol(symbol, days);
        if (!ohlcvRecords.isEmpty()) {
            List<DailyPrice> prices =
                    new ArrayList<>(
                            ohlcvRecords.stream()
                                    .map(r -> new DailyPrice(r.date(), r.close()))
                                    .toList());
            appendLatestIntradayPrice(symbol, prices);
            return prices;
        }
        log.info("No OHLCV data for {}, falling back to Finnhub", symbol);
        return priceQuoteRepository.findDailyClosingPrices(symbol, days);
    }

    private void appendLatestIntradayPrice(String symbol, List<DailyPrice> prices) {
        Optional<PricedAt> latestEntry = livePriceCache.getEntry(symbol);
        if (latestEntry.isEmpty()) {
            log.debug("No live price for {}, using OHLCV data only", symbol);
            return;
        }
        PricedAt entry = latestEntry.get();
        LocalDate quoteDate = entry.updatedAt().atZone(NY_ZONE).toLocalDate();
        LocalDate lastOhlcvDate = prices.getLast().getDate();

        if (quoteDate.isAfter(lastOhlcvDate)) {
            prices.add(new DailyPrice(quoteDate, entry.price()));
        }
    }
}
