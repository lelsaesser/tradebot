package org.tradelite.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.tradelite.common.OhlcvRecord;
import org.tradelite.repository.OhlcvRepository;
import org.tradelite.repository.PriceQuoteRepository;
import org.tradelite.service.model.DailyPrice;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyPriceProvider {

    private final OhlcvRepository ohlcvRepository;
    private final PriceQuoteRepository priceQuoteRepository;

    public List<DailyPrice> findDailyClosingPrices(String symbol, int days) {
        List<OhlcvRecord> ohlcvRecords = ohlcvRepository.findBySymbol(symbol, days);
        if (!ohlcvRecords.isEmpty()) {
            return ohlcvRecords.stream().map(r -> new DailyPrice(r.date(), r.close())).toList();
        }
        log.info("No OHLCV data for {}, falling back to Finnhub", symbol);
        return priceQuoteRepository.findDailyClosingPrices(symbol, days);
    }
}
