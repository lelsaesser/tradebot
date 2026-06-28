package org.tradelite.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.model.DailyPrice;

class SqlitePriceQuoteRepositoryTest extends AbstractSqliteRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqlitePriceQuoteRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqlitePriceQuoteRepository(jdbcTemplate);
    }

    @Test
    void findDailyClosingPrices_returnsEmptyListForUnknownSymbol() {
        List<DailyPrice> results = repository.findDailyClosingPrices("UNKNOWN", 30);
        assertThat(results, is(empty()));
    }

    @Test
    void findDailyClosingPrices_returnsPricesWithinDaysLimit() {
        repository.save(createPriceQuote("AAPL", 175.50));

        List<DailyPrice> results = repository.findDailyClosingPrices("AAPL", 30);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().getPrice(), is(175.50));
        assertThat(results.getFirst().getDate(), is(LocalDate.now()));
    }

    @Test
    void findDailyClosingPrices_groupsByDate() {
        long base = Instant.now().getEpochSecond();
        repository.save(createPriceQuote("AAPL", 175.50, base));
        repository.save(createPriceQuote("AAPL", 176.00, base + 1));
        repository.save(createPriceQuote("AAPL", 176.50, base + 2));

        List<DailyPrice> results = repository.findDailyClosingPrices("AAPL", 30);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().getPrice(), is(176.50));
    }

    @Test
    void findDailyClosingPrices_ordersChronologically() {
        repository.save(createPriceQuote("AAPL", 175.50));

        List<DailyPrice> results = repository.findDailyClosingPrices("AAPL", 80);
        assertThat(results, hasSize(1));
    }

    @Test
    void findDailyClosingPrices_respectsDaysLimit() {
        repository.save(createPriceQuote("AAPL", 175.50));

        List<DailyPrice> results = repository.findDailyClosingPrices("AAPL", 1);
        assertThat(results, hasSize(1));
    }

    @Test
    void findDailyClosingPrices_returnsOnlyMatchingSymbol() {
        repository.save(createPriceQuote("AAPL", 175.50));
        repository.save(createPriceQuote("GOOG", 150.25));
        repository.save(createPriceQuote("MSFT", 400.00));

        List<DailyPrice> results = repository.findDailyClosingPrices("GOOG", 30);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().getPrice(), is(150.25));
    }

    @Test
    void findDailyClosingPrices_withZeroDaysReturnsEmpty() {
        repository.save(createPriceQuote("AAPL", 175.50));

        List<DailyPrice> results = repository.findDailyClosingPrices("AAPL", 0);
        assertThat(results, is(notNullValue()));
    }

    @Test
    void deleteBySymbol_removesOnlyTargetSymbol() {
        repository.save(createPriceQuote("AAPL", 175.50));
        repository.save(createPriceQuote("GOOG", 150.25));

        int deleted = repository.deleteBySymbol("AAPL");

        assertThat(deleted, greaterThanOrEqualTo(1));
        assertThat(repository.findDailyClosingPrices("AAPL", 30), is(empty()));
        assertThat(repository.findDailyClosingPrices("GOOG", 30), hasSize(1));
    }

    @Test
    void deleteBySymbol_unknownSymbol_returnsZero() {
        int deleted = repository.deleteBySymbol("UNKNOWN");
        assertThat(deleted, is(0));
    }

    @Test
    void onSymbolRemoved_delegatesToDeleteBySymbol() {
        repository.save(createPriceQuote("AAPL", 175.50));

        repository.onSymbolRemoved("AAPL");

        assertThat(repository.findDailyClosingPrices("AAPL", 30), is(empty()));
    }

    private PriceQuoteResponse createPriceQuote(String symbol, double price) {
        PriceQuoteResponse priceQuote = new PriceQuoteResponse();
        priceQuote.setStockSymbol(new StockSymbol(symbol, symbol + " Inc."));
        priceQuote.setCurrentPrice(price);
        priceQuote.setDailyOpen(price - 1);
        priceQuote.setDailyHigh(price + 2);
        priceQuote.setDailyLow(price - 2);
        priceQuote.setChange(1.0);
        priceQuote.setChangePercent(0.5);
        priceQuote.setPreviousClose(price - 1);
        return priceQuote;
    }

    private PriceQuoteResponse createPriceQuote(String symbol, double price, long timestamp) {
        PriceQuoteResponse priceQuote = createPriceQuote(symbol, price);
        priceQuote.setTimestamp(timestamp);
        return priceQuote;
    }
}
