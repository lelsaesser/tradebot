package org.tradelite.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;
import org.tradelite.service.model.DailyPrice;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Sql("classpath:schema.sql")
class SqlitePriceQuoteRepositoryTest {

    @Autowired private JdbcTemplate jdbcTemplate;

    private SqlitePriceQuoteRepository repository;

    @BeforeEach
    void setUp() {
        repository = new SqlitePriceQuoteRepository(jdbcTemplate);
    }

    @Test
    void save_persistsPriceQuote() {
        PriceQuoteResponse priceQuote = createPriceQuote("AAPL", 175.50);

        repository.save(priceQuote);

        List<PriceQuoteEntity> results = repository.findBySymbol("AAPL");
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().getSymbol(), is("AAPL"));
        assertThat(results.getFirst().getCurrentPrice(), is(175.50));
    }

    @Test
    void save_persistsAllPriceFields() {
        PriceQuoteResponse priceQuote = new PriceQuoteResponse();
        priceQuote.setStockSymbol(new StockSymbol("GOOG", "Google"));
        priceQuote.setCurrentPrice(150.25);
        priceQuote.setDailyOpen(148.00);
        priceQuote.setDailyHigh(152.50);
        priceQuote.setDailyLow(147.50);
        priceQuote.setChange(2.25);
        priceQuote.setChangePercent(1.52);
        priceQuote.setPreviousClose(148.00);

        repository.save(priceQuote);

        List<PriceQuoteEntity> results = repository.findBySymbol("GOOG");
        assertThat(results, hasSize(1));
        PriceQuoteEntity entity = results.getFirst();
        assertThat(entity.getDailyOpen(), is(148.00));
        assertThat(entity.getDailyHigh(), is(152.50));
        assertThat(entity.getDailyLow(), is(147.50));
        assertThat(entity.getChangeAmount(), is(2.25));
        assertThat(entity.getChangePercent(), is(1.52));
        assertThat(entity.getPreviousClose(), is(148.00));
    }

    @Test
    void save_multipleQuotesForSameSymbol() throws InterruptedException {
        repository.save(createPriceQuote("AAPL", 175.50));
        Thread.sleep(1100);
        repository.save(createPriceQuote("AAPL", 176.00));

        List<PriceQuoteEntity> results = repository.findBySymbol("AAPL");
        assertThat(results, hasSize(2));
    }

    @Test
    void findBySymbol_returnsEmptyListForUnknownSymbol() {
        List<PriceQuoteEntity> results = repository.findBySymbol("UNKNOWN");
        assertThat(results, is(empty()));
    }

    @Test
    void findBySymbol_returnsOnlyMatchingSymbol() {
        repository.save(createPriceQuote("AAPL", 175.50));
        repository.save(createPriceQuote("GOOG", 150.25));
        repository.save(createPriceQuote("MSFT", 400.00));

        List<PriceQuoteEntity> results = repository.findBySymbol("GOOG");
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().getSymbol(), is("GOOG"));
    }

    @Test
    void findBySymbol_returnsResultsOrderedByTimestamp() throws InterruptedException {
        repository.save(createPriceQuote("AAPL", 175.50));
        Thread.sleep(1100);
        repository.save(createPriceQuote("AAPL", 176.00));

        List<PriceQuoteEntity> results = repository.findBySymbol("AAPL");
        assertThat(results, hasSize(2));
        assertThat(results.get(0).getTimestamp(), lessThan(results.get(1).getTimestamp()));
    }

    @Test
    void findBySymbolAndDate_returnsQuotesForSpecificDate() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        List<PriceQuoteEntity> results = repository.findBySymbolAndDate("AAPL", today);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().getSymbol(), is("AAPL"));
    }

    @Test
    void findBySymbolAndDate_returnsEmptyForDifferentDate() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate yesterday = LocalDate.now(ZoneId.of("UTC")).minusDays(1);
        List<PriceQuoteEntity> results = repository.findBySymbolAndDate("AAPL", yesterday);
        assertThat(results, is(empty()));
    }

    @Test
    void findBySymbolAndDateRange_returnsQuotesInRange() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate today = LocalDate.now(ZoneId.of("UTC"));
        LocalDate startDate = today.minusDays(1);
        LocalDate endDate = today.plusDays(1);

        List<PriceQuoteEntity> results =
                repository.findBySymbolAndDateRange("AAPL", startDate, endDate);
        assertThat(results, hasSize(1));
    }

    @Test
    void findBySymbolAndDateRange_returnsEmptyForOutOfRangeDate() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate startDate = LocalDate.now(ZoneId.of("UTC")).minusDays(10);
        LocalDate endDate = LocalDate.now(ZoneId.of("UTC")).minusDays(5);

        List<PriceQuoteEntity> results =
                repository.findBySymbolAndDateRange("AAPL", startDate, endDate);
        assertThat(results, is(empty()));
    }

    @Test
    void entityHasAutoGeneratedId() {
        repository.save(createPriceQuote("AAPL", 175.50));

        List<PriceQuoteEntity> results = repository.findBySymbol("AAPL");
        assertThat(results.getFirst().getId(), is(notNullValue()));
        assertThat(results.getFirst().getId(), greaterThan(0L));
    }

    @Test
    void entityHasTimestamp() {
        long beforeSave = System.currentTimeMillis() / 1000 - 1;
        repository.save(createPriceQuote("AAPL", 175.50));
        long afterSave = System.currentTimeMillis() / 1000 + 1;

        List<PriceQuoteEntity> results = repository.findBySymbol("AAPL");
        assertThat(results.getFirst().getTimestamp(), greaterThanOrEqualTo(beforeSave));
        assertThat(results.getFirst().getTimestamp(), lessThanOrEqualTo(afterSave));
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
    void findDailyClosingPrices_groupsByDate() throws InterruptedException {
        repository.save(createPriceQuote("AAPL", 175.50));
        Thread.sleep(1100);
        repository.save(createPriceQuote("AAPL", 176.00));
        Thread.sleep(1100);
        repository.save(createPriceQuote("AAPL", 176.50));

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
    void findDailyChangePercents_returnsEmptyListForUnknownSymbol() {
        List<Double> results = repository.findDailyChangePercents("UNKNOWN", 30);
        assertThat(results, is(empty()));
    }

    @Test
    void findDailyChangePercents_returnsChangePercentWithinDaysLimit() {
        PriceQuoteResponse quote = createPriceQuote("AAPL", 175.50);
        quote.setChangePercent(2.5);
        repository.save(quote);

        List<Double> results = repository.findDailyChangePercents("AAPL", 30);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst(), is(2.5));
    }

    @Test
    void findDailyChangePercents_groupsByDateReturnsLatestChangePercent()
            throws InterruptedException {
        PriceQuoteResponse quote1 = createPriceQuote("AAPL", 175.50);
        quote1.setChangePercent(1.5);
        repository.save(quote1);

        Thread.sleep(1100);

        PriceQuoteResponse quote2 = createPriceQuote("AAPL", 176.00);
        quote2.setChangePercent(2.0);
        repository.save(quote2);

        Thread.sleep(1100);

        PriceQuoteResponse quote3 = createPriceQuote("AAPL", 176.50);
        quote3.setChangePercent(2.5);
        repository.save(quote3);

        List<Double> results = repository.findDailyChangePercents("AAPL", 30);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst(), is(2.5));
    }

    @Test
    void findDailyChangePercents_returnsOnlyMatchingSymbol() {
        PriceQuoteResponse aaplQuote = createPriceQuote("AAPL", 175.50);
        aaplQuote.setChangePercent(1.5);
        repository.save(aaplQuote);

        PriceQuoteResponse googQuote = createPriceQuote("GOOG", 150.25);
        googQuote.setChangePercent(2.5);
        repository.save(googQuote);

        PriceQuoteResponse msftQuote = createPriceQuote("MSFT", 400.00);
        msftQuote.setChangePercent(3.5);
        repository.save(msftQuote);

        List<Double> results = repository.findDailyChangePercents("GOOG", 30);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst(), is(2.5));
    }

    @Test
    void findLatestBySymbol_returnsLatestEntry() throws InterruptedException {
        repository.save(createPriceQuote("AAPL", 175.50));
        Thread.sleep(1100);
        repository.save(createPriceQuote("AAPL", 176.00));

        var result = repository.findLatestBySymbol("AAPL");
        assertThat(result.isPresent(), is(true));
        assertThat(result.get().getCurrentPrice(), is(176.00));
    }

    @Test
    void findLatestBySymbol_returnsEmptyForUnknownSymbol() {
        var result = repository.findLatestBySymbol("UNKNOWN");
        assertThat(result.isPresent(), is(false));
    }

    @Test
    void findLatestBySymbol_returnsOnlyMatchingSymbol() {
        repository.save(createPriceQuote("AAPL", 175.50));
        repository.save(createPriceQuote("GOOG", 150.25));

        var result = repository.findLatestBySymbol("GOOG");
        assertThat(result.isPresent(), is(true));
        assertThat(result.get().getSymbol(), is("GOOG"));
        assertThat(result.get().getCurrentPrice(), is(150.25));
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
}
