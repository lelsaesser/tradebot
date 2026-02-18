package org.tradelite.repository;

import java.time.LocalDate;
import java.util.List;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;

public interface PriceQuoteRepository {

    void save(PriceQuoteResponse priceQuote);

    List<PriceQuoteEntity> findBySymbol(String symbol);

    List<PriceQuoteEntity> findBySymbolAndDate(String symbol, LocalDate date);

    List<PriceQuoteEntity> findBySymbolAndDateRange(
            String symbol, LocalDate startDate, LocalDate endDate);
}
