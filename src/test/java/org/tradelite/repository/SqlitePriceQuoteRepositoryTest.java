package org.tradelite.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;
import org.tradelite.common.StockSymbol;

class SqlitePriceQuoteRepositoryTest {

    private SqlitePriceQuoteRepository repository;
    private String testDbPath;

    @BeforeEach
    void setUp() {
        // Use a unique temp file for each test to avoid conflicts
        testDbPath = "target/test-db-" + UUID.randomUUID() + ".db";
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + testDbPath);
        repository = new SqlitePriceQuoteRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        // Clean up test database file
        File dbFile = new File(testDbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
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

        // Wait at least 1 second to ensure different timestamps (timestamp is in seconds)
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

        // Wait at least 1 second to ensure different timestamps (timestamp is in seconds)
        Thread.sleep(1100);

        repository.save(createPriceQuote("AAPL", 176.00));

        List<PriceQuoteEntity> results = repository.findBySymbol("AAPL");
        assertThat(results, hasSize(2));
        assertThat(results.get(0).getTimestamp(), lessThan(results.get(1).getTimestamp()));
    }

    @Test
    void findBySymbolAndDate_returnsQuotesForSpecificDate() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        List<PriceQuoteEntity> results = repository.findBySymbolAndDate("AAPL", today);

        assertThat(results, hasSize(1));
        assertThat(results.getFirst().getSymbol(), is("AAPL"));
    }

    @Test
    void findBySymbolAndDate_returnsEmptyForDifferentDate() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate yesterday = LocalDate.now(ZoneId.of("America/New_York")).minusDays(1);
        List<PriceQuoteEntity> results = repository.findBySymbolAndDate("AAPL", yesterday);

        assertThat(results, is(empty()));
    }

    @Test
    void findBySymbolAndDateRange_returnsQuotesInRange() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate today = LocalDate.now(ZoneId.of("America/New_York"));
        LocalDate startDate = today.minusDays(1);
        LocalDate endDate = today.plusDays(1);

        List<PriceQuoteEntity> results =
                repository.findBySymbolAndDateRange("AAPL", startDate, endDate);

        assertThat(results, hasSize(1));
    }

    @Test
    void findBySymbolAndDateRange_returnsEmptyForOutOfRangeDate() {
        repository.save(createPriceQuote("AAPL", 175.50));

        LocalDate startDate = LocalDate.now(ZoneId.of("America/New_York")).minusDays(10);
        LocalDate endDate = LocalDate.now(ZoneId.of("America/New_York")).minusDays(5);

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
    void constructor_throwsExceptionOnSchemaInitFailure() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        assertThrows(
                IllegalStateException.class, () -> new SqlitePriceQuoteRepository(mockDataSource));
    }

    @Test
    void save_throwsExceptionOnDatabaseError() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        // First call for schema init succeeds
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqlitePriceQuoteRepository repoWithMock = new SqlitePriceQuoteRepository(mockDataSource);

        // Second call for save fails
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        PriceQuoteResponse priceQuote = createPriceQuote("AAPL", 175.50);
        assertThrows(IllegalStateException.class, () -> repoWithMock.save(priceQuote));
    }

    @Test
    void findBySymbol_throwsExceptionOnDatabaseError() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        // First call for schema init succeeds
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqlitePriceQuoteRepository repoWithMock = new SqlitePriceQuoteRepository(mockDataSource);

        // Second call for findBySymbol fails
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        assertThrows(IllegalStateException.class, () -> repoWithMock.findBySymbol("AAPL"));
    }

    @Test
    void findBySymbolAndDate_throwsExceptionOnDatabaseError() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        // First call for schema init succeeds
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqlitePriceQuoteRepository repoWithMock = new SqlitePriceQuoteRepository(mockDataSource);

        // Second call for findBySymbolAndDate fails
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        LocalDate today = LocalDate.now();
        assertThrows(
                IllegalStateException.class, () -> repoWithMock.findBySymbolAndDate("AAPL", today));
    }

    @Test
    void findBySymbolAndDateRange_throwsExceptionOnDatabaseError() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        // First call for schema init succeeds
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqlitePriceQuoteRepository repoWithMock = new SqlitePriceQuoteRepository(mockDataSource);

        // Second call for findBySymbolAndDateRange fails
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now();
        assertThrows(
                IllegalStateException.class,
                () -> repoWithMock.findBySymbolAndDateRange("AAPL", startDate, endDate));
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
