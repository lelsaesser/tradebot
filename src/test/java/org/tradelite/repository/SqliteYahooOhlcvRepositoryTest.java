package org.tradelite.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.tradelite.client.yahoo.dto.YahooOhlcvRecord;

class SqliteYahooOhlcvRepositoryTest {

    private SqliteYahooOhlcvRepository repository;
    private String testDbPath;

    @BeforeEach
    void setUp() {
        testDbPath = "target/test-yahoo-ohlcv-" + UUID.randomUUID() + ".db";
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + testDbPath);
        repository = new SqliteYahooOhlcvRepository(dataSource);
    }

    @AfterEach
    void tearDown() {
        File dbFile = new File(testDbPath);
        if (dbFile.exists()) {
            dbFile.delete();
        }
    }

    @Test
    void saveAll_persistsRecords() {
        List<YahooOhlcvRecord> records =
                List.of(createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 175.0));

        repository.saveAll(records);

        List<YahooOhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().symbol(), is("AAPL"));
        assertThat(results.getFirst().close(), is(175.0));
    }

    @Test
    void saveAll_persistsAllFields() {
        YahooOhlcvRecord ohlcvRecord =
                new YahooOhlcvRecord(
                        "GOOG",
                        LocalDate.of(2026, 4, 10),
                        148.0,
                        152.5,
                        147.5,
                        150.25,
                        149.80,
                        5_000_000L);

        repository.saveAll(List.of(ohlcvRecord));

        List<YahooOhlcvRecord> results = repository.findBySymbol("GOOG", 365);
        assertThat(results, hasSize(1));
        YahooOhlcvRecord result = results.getFirst();
        assertThat(result.open(), is(148.0));
        assertThat(result.high(), is(152.5));
        assertThat(result.low(), is(147.5));
        assertThat(result.close(), is(150.25));
        assertThat(result.adjClose(), is(149.80));
        assertThat(result.volume(), is(5_000_000L));
    }

    @Test
    void saveAll_multipleRecordsForSameSymbol() {
        List<YahooOhlcvRecord> records =
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 9), 170.0, 175.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 175.0, 176.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 11), 176.0, 178.0));

        repository.saveAll(records);

        List<YahooOhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, hasSize(3));
    }

    @Test
    void saveAll_upsertUpdatesExistingRecord() {
        YahooOhlcvRecord original = createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 175.0);
        repository.saveAll(List.of(original));

        YahooOhlcvRecord updated = createRecord("AAPL", LocalDate.of(2026, 4, 10), 172.0, 180.0);
        repository.saveAll(List.of(updated));

        List<YahooOhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().open(), is(172.0));
        assertThat(results.getFirst().close(), is(180.0));
    }

    @Test
    void saveAll_emptyList() {
        repository.saveAll(List.of());

        List<YahooOhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, is(empty()));
    }

    @Test
    void findBySymbol_returnsEmptyForUnknownSymbol() {
        List<YahooOhlcvRecord> results = repository.findBySymbol("UNKNOWN", 365);
        assertThat(results, is(empty()));
    }

    @Test
    void findBySymbol_returnsOnlyMatchingSymbol() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 175.0),
                        createRecord("GOOG", LocalDate.of(2026, 4, 10), 148.0, 150.0),
                        createRecord("MSFT", LocalDate.of(2026, 4, 10), 395.0, 400.0)));

        List<YahooOhlcvRecord> results = repository.findBySymbol("GOOG", 365);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().symbol(), is("GOOG"));
    }

    @Test
    void findBySymbol_returnsSortedByDateAscending() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 11), 176.0, 178.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 9), 170.0, 172.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 172.0, 175.0)));

        List<YahooOhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, hasSize(3));
        assertThat(results.get(0).date(), is(LocalDate.of(2026, 4, 9)));
        assertThat(results.get(1).date(), is(LocalDate.of(2026, 4, 10)));
        assertThat(results.get(2).date(), is(LocalDate.of(2026, 4, 11)));
    }

    @Test
    void findBySymbol_respectsDaysLimit() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.now().minusDays(10), 170.0, 172.0),
                        createRecord("AAPL", LocalDate.now().minusDays(3), 175.0, 176.0),
                        createRecord("AAPL", LocalDate.now(), 178.0, 180.0)));

        List<YahooOhlcvRecord> results = repository.findBySymbol("AAPL", 5);
        assertThat(results, hasSize(2));
        assertThat(results.getFirst().close(), is(176.0));
        assertThat(results.getLast().close(), is(180.0));
    }

    @Test
    void findBySymbol_caseSensitive() {
        repository.saveAll(List.of(createRecord("XLK", LocalDate.of(2026, 4, 10), 200.0, 205.0)));

        List<YahooOhlcvRecord> upperResult = repository.findBySymbol("XLK", 365);
        List<YahooOhlcvRecord> lowerResult = repository.findBySymbol("xlk", 365);

        assertThat(upperResult, hasSize(1));
        assertThat(lowerResult, is(empty()));
    }

    @Test
    void findBySymbol_largeVolume() {
        YahooOhlcvRecord ohlcvRecord =
                new YahooOhlcvRecord(
                        "SPY",
                        LocalDate.of(2026, 4, 10),
                        500.0,
                        505.0,
                        498.0,
                        503.0,
                        503.0,
                        3_000_000_000L);

        repository.saveAll(List.of(ohlcvRecord));

        List<YahooOhlcvRecord> results = repository.findBySymbol("SPY", 365);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().volume(), is(3_000_000_000L));
    }

    @Test
    void saveAll_multipleSymbolsStoredIndependently() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 175.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 11), 175.0, 178.0)));
        repository.saveAll(List.of(createRecord("GOOG", LocalDate.of(2026, 4, 10), 148.0, 150.0)));

        List<YahooOhlcvRecord> aaplResults = repository.findBySymbol("AAPL", 365);
        List<YahooOhlcvRecord> googResults = repository.findBySymbol("GOOG", 365);

        assertThat(aaplResults, hasSize(2));
        assertThat(googResults, hasSize(1));
    }

    @Test
    void constructor_throwsExceptionOnSchemaInitFailure() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        assertThrows(
                IllegalStateException.class, () -> new SqliteYahooOhlcvRepository(mockDataSource));
    }

    @Test
    void saveAll_throwsExceptionOnDatabaseError() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqliteYahooOhlcvRepository repoWithMock = new SqliteYahooOhlcvRepository(mockDataSource);

        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        List<YahooOhlcvRecord> records =
                List.of(createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 175.0));
        assertThrows(IllegalStateException.class, () -> repoWithMock.saveAll(records));
    }

    @Test
    void findBySymbol_throwsExceptionOnDatabaseError() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqliteYahooOhlcvRepository repoWithMock = new SqliteYahooOhlcvRepository(mockDataSource);

        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        assertThrows(IllegalStateException.class, () -> repoWithMock.findBySymbol("AAPL", 30));
    }

    private YahooOhlcvRecord createRecord(
            String symbol, LocalDate date, double open, double close) {
        return new YahooOhlcvRecord(
                symbol, date, open, open + 5.0, open - 2.0, close, close - 0.5, 1_000_000L);
    }
}
