package org.tradelite.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.tradelite.client.yahoo.dto.YahooOhlcvRecord;

@Slf4j
@Repository
public class SqliteYahooOhlcvRepository implements YahooOhlcvRepository {

    private final DataSource dataSource;

    @Autowired
    public SqliteYahooOhlcvRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        initializeSchema();
    }

    private void initializeSchema() {
        String createTableSql =
                """
                CREATE TABLE IF NOT EXISTS yahoo_daily_ohlcv (
                    symbol TEXT NOT NULL,
                    date TEXT NOT NULL,
                    open REAL NOT NULL,
                    high REAL NOT NULL,
                    low REAL NOT NULL,
                    close REAL NOT NULL,
                    adj_close REAL NOT NULL,
                    volume INTEGER NOT NULL,
                    UNIQUE(symbol, date)
                )
                """;

        String createSymbolIndexSql =
                "CREATE INDEX IF NOT EXISTS idx_yahoo_daily_ohlcv_symbol"
                        + " ON yahoo_daily_ohlcv(symbol)";

        String createCompositeIndexSql =
                "CREATE INDEX IF NOT EXISTS idx_yahoo_daily_ohlcv_symbol_date"
                        + " ON yahoo_daily_ohlcv(symbol, date)";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(createSymbolIndexSql);
            stmt.execute(createCompositeIndexSql);
            log.info("SQLite yahoo_daily_ohlcv table and indexes initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize SQLite yahoo_daily_ohlcv schema", e);
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    @Override
    public void saveAll(List<YahooOhlcvRecord> records) {
        String sql =
                """
                INSERT OR REPLACE INTO yahoo_daily_ohlcv
                (symbol, date, open, high, low, close, adj_close, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (YahooOhlcvRecord ohlcv : records) {
                pstmt.setString(1, ohlcv.symbol());
                pstmt.setString(2, ohlcv.date().toString());
                pstmt.setDouble(3, ohlcv.open());
                pstmt.setDouble(4, ohlcv.high());
                pstmt.setDouble(5, ohlcv.low());
                pstmt.setDouble(6, ohlcv.close());
                pstmt.setDouble(7, ohlcv.adjClose());
                pstmt.setLong(8, ohlcv.volume());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            log.debug("Saved {} Yahoo OHLCV records", records.size());
        } catch (SQLException e) {
            log.error("Failed to save Yahoo OHLCV records", e);
            throw new IllegalStateException("Failed to save Yahoo OHLCV records", e);
        }
    }

    @Override
    public List<YahooOhlcvRecord> findBySymbol(String symbol, int days) {
        String sql =
                """
                SELECT symbol, date, open, high, low, close, adj_close, volume
                FROM yahoo_daily_ohlcv
                WHERE symbol = ? AND date >= date('now', ?)
                ORDER BY date ASC
                """;

        List<YahooOhlcvRecord> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setString(2, "-" + days + " days");
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    results.add(mapResultSetToRecord(rs));
                }
            }
        } catch (SQLException e) {
            log.error(
                    "Failed to find Yahoo OHLCV records for symbol {} over {} days",
                    symbol,
                    days,
                    e);
            throw new IllegalStateException("Failed to find Yahoo OHLCV records", e);
        }
        return results;
    }

    private YahooOhlcvRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        return new YahooOhlcvRecord(
                rs.getString("symbol"),
                LocalDate.parse(rs.getString("date")),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getDouble("adj_close"),
                rs.getLong("volume"));
    }
}
