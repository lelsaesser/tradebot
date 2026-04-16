package org.tradelite.service;

import java.time.Instant;
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
import org.tradelite.repository.PriceQuoteEntity;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceProvider {

    private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

    private final OhlcvRepository ohlcvRepository;
    private final PriceQuoteRepository priceQuoteRepository;

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
        Optional<PriceQuoteEntity> latestQuote = priceQuoteRepository.findLatestBySymbol(symbol);
        if (latestQuote.isEmpty()) {
            log.debug("No Finnhub intraday price for {}, using OHLCV data only", symbol);
            return;
        }
        PriceQuoteEntity quote = latestQuote.get();
        LocalDate quoteDate =
                Instant.ofEpochSecond(quote.getTimestamp()).atZone(NY_ZONE).toLocalDate();
        LocalDate lastOhlcvDate = prices.getLast().getDate();

        if (quoteDate.isAfter(lastOhlcvDate)) {
            prices.add(new DailyPrice(quoteDate, quote.getCurrentPrice()));
        }
    }
}
