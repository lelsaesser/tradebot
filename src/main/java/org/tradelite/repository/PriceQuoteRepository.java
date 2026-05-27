package org.tradelite.repository;

import java.util.List;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.service.model.DailyPrice;

public interface PriceQuoteRepository {

    void save(PriceQuoteResponse priceQuote);

    void saveAll(List<PriceQuoteResponse> priceQuotes);

    /**
     * Finds daily closing prices for a symbol for the last N calendar days.
     *
     * <p>Returns one price per day (the latest price recorded for that day), sorted by date
     * ascending. This is optimized for RS/EMA calculations that need daily prices.
     *
     * @param symbol The stock ticker symbol
     * @param days Number of calendar days to look back
     * @return List of daily prices, sorted by date ascending
     */
    List<DailyPrice> findDailyClosingPrices(String symbol, int days);
}
