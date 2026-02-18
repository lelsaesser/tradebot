package org.tradelite.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.tradelite.client.finnhub.dto.PriceQuoteResponse;

@Slf4j
@Repository
public class SqlitePriceQuoteRepository implements PriceQuoteRepository {

    private static final ZoneId NEW_YORK_ZONE = ZoneId.of("America/New_York");

    private final DataSource dataSource;

    @Autowired
    public SqlitePriceQuoteRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        initializeSchema();
    }

    private void initializeSchema() {
        String createTableSql =
                """
                CREATE TABLE IF NOT EXISTS price_quotes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    symbol TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    current_price REAL NOT NULL,
                    daily_open REAL,
                    daily_high REAL,
                    daily_low REAL,
                    change_amount REAL,
                    change_percent REAL,
                    previous_close REAL,
                    UNIQUE(symbol, timestamp)
                )
                """;

        String createSymbolIndexSql =
                "CREATE INDEX IF NOT EXISTS idx_price_quotes_symbol ON price_quotes(symbol)";

        String createTimestampIndexSql =
                "CREATE INDEX IF NOT EXISTS idx_price_quotes_timestamp ON price_quotes(timestamp)";

        String createCompositeIndexSql =
                "CREATE INDEX IF NOT EXISTS idx_price_quotes_symbol_timestamp "
                        + "ON price_quotes(symbol, timestamp)";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(createSymbolIndexSql);
            stmt.execute(createTimestampIndexSql);
            stmt.execute(createCompositeIndexSql);
            log.info("SQLite price_quotes table and indexes initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize SQLite schema", e);
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    @Override
    public void save(PriceQuoteResponse priceQuote) {
        String sql =
                """
                INSERT OR REPLACE INTO price_quotes
                (symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                 change_amount, change_percent, previous_close)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        long timestamp = Instant.now().getEpochSecond();

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, priceQuote.getStockSymbol().getTicker());
            pstmt.setLong(2, timestamp);
            pstmt.setDouble(3, priceQuote.getCurrentPrice());
            pstmt.setDouble(4, priceQuote.getDailyOpen());
            pstmt.setDouble(5, priceQuote.getDailyHigh());
            pstmt.setDouble(6, priceQuote.getDailyLow());
            pstmt.setDouble(7, priceQuote.getChange());
            pstmt.setDouble(8, priceQuote.getChangePercent());
            pstmt.setDouble(9, priceQuote.getPreviousClose());
            pstmt.executeUpdate();
            log.debug(
                    "Saved price quote for {} at timestamp {}",
                    priceQuote.getStockSymbol().getTicker(),
                    timestamp);
        } catch (SQLException e) {
            log.error(
                    "Failed to save price quote for {}",
                    priceQuote.getStockSymbol().getTicker(),
                    e);
            throw new IllegalStateException("Failed to save price quote", e);
        }
    }

    @Override
    public List<PriceQuoteEntity> findBySymbol(String symbol) {
        String sql =
                """
                SELECT id, symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                       change_amount, change_percent, previous_close
                FROM price_quotes
                WHERE symbol = ?
                ORDER BY timestamp ASC
                """;

        List<PriceQuoteEntity> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find price quotes for symbol {}", symbol, e);
            throw new IllegalStateException("Failed to find price quotes", e);
        }
        return results;
    }

    @Override
    public List<PriceQuoteEntity> findBySymbolAndDate(String symbol, LocalDate date) {
        long startOfDay = date.atStartOfDay(NEW_YORK_ZONE).toEpochSecond();
        long endOfDay = date.plusDays(1).atStartOfDay(NEW_YORK_ZONE).toEpochSecond();

        return findBySymbolAndTimestampRange(symbol, startOfDay, endOfDay);
    }

    @Override
    public List<PriceQuoteEntity> findBySymbolAndDateRange(
            String symbol, LocalDate startDate, LocalDate endDate) {
        long startTimestamp = startDate.atStartOfDay(NEW_YORK_ZONE).toEpochSecond();
        long endTimestamp = endDate.plusDays(1).atStartOfDay(NEW_YORK_ZONE).toEpochSecond();

        return findBySymbolAndTimestampRange(symbol, startTimestamp, endTimestamp);
    }

    private List<PriceQuoteEntity> findBySymbolAndTimestampRange(
            String symbol, long startTimestamp, long endTimestamp) {
        String sql =
                """
                SELECT id, symbol, timestamp, current_price, daily_open, daily_high, daily_low,
                       change_amount, change_percent, previous_close
                FROM price_quotes
                WHERE symbol = ? AND timestamp >= ? AND timestamp < ?
                ORDER BY timestamp ASC
                """;

        List<PriceQuoteEntity> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setLong(2, startTimestamp);
            pstmt.setLong(3, endTimestamp);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToEntity(rs));
                }
            }
        } catch (SQLException e) {
            log.error(
                    "Failed to find price quotes for symbol {} in timestamp range [{}, {})",
                    symbol,
                    startTimestamp,
                    endTimestamp,
                    e);
            throw new IllegalStateException("Failed to find price quotes", e);
        }
        return results;
    }

    private PriceQuoteEntity mapResultSetToEntity(ResultSet rs) throws SQLException {
        return PriceQuoteEntity.builder()
                .id(rs.getLong("id"))
                .symbol(rs.getString("symbol"))
                .timestamp(rs.getLong("timestamp"))
                .currentPrice(rs.getDouble("current_price"))
                .dailyOpen(rs.getDouble("daily_open"))
                .dailyHigh(rs.getDouble("daily_high"))
                .dailyLow(rs.getDouble("daily_low"))
                .changeAmount(rs.getDouble("change_amount"))
                .changePercent(rs.getDouble("change_percent"))
                .previousClose(rs.getDouble("previous_close"))
                .build();
    }
}
