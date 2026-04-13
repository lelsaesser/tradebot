package org.tradelite.repository;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sqlite.SQLiteDataSource;
import org.tradelite.common.OhlcvRecord;

class SqliteOhlcvRepositoryTest {

    private SqliteOhlcvRepository repository;

    @BeforeEach
    void setUp() {
        String testDbPath = "target/test-ohlcv-" + UUID.randomUUID() + ".db";
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + testDbPath);
        repository = new SqliteOhlcvRepository(dataSource);
    }

    @Test
    void saveAll_persistsRecords() {
        List<OhlcvRecord> records =
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 9), 170.0, 174.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 174.0, 178.0));

        repository.saveAll(records);

        List<OhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, hasSize(2));
    }

    @Test
    void saveAll_persistsAllFields() {
        OhlcvRecord ohlcvRecord =
                new OhlcvRecord(
                        "GOOG",
                        LocalDate.of(2026, 4, 10),
                        150.25,
                        155.75,
                        148.30,
                        149.80,
                        2_500_000L);

        repository.saveAll(List.of(ohlcvRecord));

        List<OhlcvRecord> results = repository.findBySymbol("GOOG", 365);
        assertThat(results, hasSize(1));

        OhlcvRecord result = results.getFirst();
        assertThat(result.symbol(), is("GOOG"));
        assertThat(result.date(), is(LocalDate.of(2026, 4, 10)));
        assertThat(result.open(), is(150.25));
        assertThat(result.high(), is(155.75));
        assertThat(result.low(), is(148.30));
        assertThat(result.close(), is(149.80));
        assertThat(result.volume(), is(2_500_000L));
    }

    @Test
    void saveAll_multipleRecordsForSameSymbol() {
        List<OhlcvRecord> records =
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 7), 165.0, 168.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 8), 168.0, 170.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 9), 170.0, 174.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 174.0, 178.0));

        repository.saveAll(records);

        List<OhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, hasSize(4));
    }

    @Test
    void saveAll_upsertUpdatesExistingRecord() {
        OhlcvRecord original = createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 175.0);
        repository.saveAll(List.of(original));

        OhlcvRecord updated = createRecord("AAPL", LocalDate.of(2026, 4, 10), 172.0, 180.0);
        repository.saveAll(List.of(updated));

        List<OhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().open(), is(172.0));
    }

    @Test
    void saveAll_emptyList() {
        repository.saveAll(List.of());

        List<OhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results, is(empty()));
    }

    @Test
    void findBySymbol_unknownSymbol_returnsEmptyList() {
        List<OhlcvRecord> results = repository.findBySymbol("UNKNOWN", 365);
        assertThat(results, is(empty()));
    }

    @Test
    void findBySymbol_filtersCorrectSymbol() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 174.0),
                        createRecord("GOOG", LocalDate.of(2026, 4, 10), 150.0, 152.0)));

        List<OhlcvRecord> results = repository.findBySymbol("GOOG", 365);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().symbol(), is("GOOG"));
    }

    @Test
    void findBySymbol_returnsRecordsOrderedByDateAsc() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 174.0, 178.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 8), 168.0, 170.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 9), 170.0, 174.0)));

        List<OhlcvRecord> results = repository.findBySymbol("AAPL", 365);
        assertThat(results.get(0).date(), is(LocalDate.of(2026, 4, 8)));
        assertThat(results.get(1).date(), is(LocalDate.of(2026, 4, 9)));
        assertThat(results.get(2).date(), is(LocalDate.of(2026, 4, 10)));
    }

    @Test
    void findBySymbol_respectsDaysLimit() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 174.0, 178.0),
                        createRecord("AAPL", LocalDate.of(2026, 4, 9), 170.0, 174.0),
                        createRecord("AAPL", LocalDate.of(2025, 1, 1), 140.0, 142.0)));

        List<OhlcvRecord> results = repository.findBySymbol("AAPL", 5);
        assertThat(results, hasSize(2));
    }

    @Test
    void findBySymbol_caseInsensitiveSymbol() {
        repository.saveAll(List.of(createRecord("XLK", LocalDate.of(2026, 4, 10), 218.0, 220.0)));

        List<OhlcvRecord> upperResult = repository.findBySymbol("XLK", 365);
        List<OhlcvRecord> lowerResult = repository.findBySymbol("xlk", 365);
        assertThat(upperResult, hasSize(1));
        assertThat(lowerResult, is(empty()));
    }

    @Test
    void saveAll_largeVolume() {
        OhlcvRecord ohlcvRecord =
                new OhlcvRecord(
                        "SPY", LocalDate.of(2026, 4, 10), 500.0, 505.0, 498.0, 503.0, 150_000_000L);

        repository.saveAll(List.of(ohlcvRecord));

        List<OhlcvRecord> results = repository.findBySymbol("SPY", 365);
        assertThat(results, hasSize(1));
        assertThat(results.getFirst().volume(), is(150_000_000L));
    }

    @Test
    void saveAll_multipleSymbols() {
        repository.saveAll(
                List.of(
                        createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 174.0),
                        createRecord("GOOG", LocalDate.of(2026, 4, 10), 150.0, 152.0),
                        createRecord("MSFT", LocalDate.of(2026, 4, 10), 400.0, 405.0)));

        List<OhlcvRecord> aaplResults = repository.findBySymbol("AAPL", 365);
        List<OhlcvRecord> googResults = repository.findBySymbol("GOOG", 365);
        assertThat(aaplResults, hasSize(1));
        assertThat(googResults, hasSize(1));
    }

    @Test
    void constructor_failedSchemaInit_throwsException() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement()).thenThrow(new SQLException("Schema init failed"));

        assertThrows(IllegalStateException.class, () -> new SqliteOhlcvRepository(mockDataSource));
    }

    @Test
    void saveAll_sqlException_throwsIllegalState() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        // First call for schema init succeeds
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqliteOhlcvRepository repoWithMock = new SqliteOhlcvRepository(mockDataSource);

        // Second call for save fails
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        List<OhlcvRecord> records =
                List.of(createRecord("AAPL", LocalDate.of(2026, 4, 10), 170.0, 174.0));

        assertThrows(IllegalStateException.class, () -> repoWithMock.saveAll(records));
    }

    @Test
    void findBySymbol_sqlException_throwsIllegalState() throws SQLException {
        DataSource mockDataSource = mock(DataSource.class);
        Connection mockConnection = mock(Connection.class);

        // First call for schema init succeeds
        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.createStatement())
                .thenReturn(mock(java.sql.Statement.class, RETURNS_DEEP_STUBS));

        SqliteOhlcvRepository repoWithMock = new SqliteOhlcvRepository(mockDataSource);

        // Second call for findBySymbol fails
        when(mockDataSource.getConnection()).thenThrow(new SQLException("Connection failed"));

        assertThrows(IllegalStateException.class, () -> repoWithMock.findBySymbol("AAPL", 365));
    }

    private OhlcvRecord createRecord(String symbol, LocalDate date, double open, double close) {
        return new OhlcvRecord(symbol, date, open, open + 5.0, open - 2.0, close, 1_000_000L);
    }
}
