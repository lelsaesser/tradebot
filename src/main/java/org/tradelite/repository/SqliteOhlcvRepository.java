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
import org.tradelite.common.OhlcvRecord;

@Slf4j
@Repository
public class SqliteOhlcvRepository implements OhlcvRepository {

    private final DataSource dataSource;

    @Autowired
    public SqliteOhlcvRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        initializeSchema();
    }

    private void initializeSchema() {
        String createTableSql =
                """
                CREATE TABLE IF NOT EXISTS twelvedata_daily_ohlcv (
                    symbol TEXT NOT NULL,
                    date TEXT NOT NULL,
                    open REAL NOT NULL,
                    high REAL NOT NULL,
                    low REAL NOT NULL,
                    close REAL NOT NULL,
                    volume INTEGER NOT NULL,
                    UNIQUE(symbol, date)
                )
                """;

        String createSymbolIndexSql =
                "CREATE INDEX IF NOT EXISTS idx_twelvedata_daily_ohlcv_symbol"
                        + " ON twelvedata_daily_ohlcv(symbol)";

        String createCompositeIndexSql =
                "CREATE INDEX IF NOT EXISTS idx_twelvedata_daily_ohlcv_symbol_date"
                        + " ON twelvedata_daily_ohlcv(symbol, date)";

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            stmt.execute(createSymbolIndexSql);
            stmt.execute(createCompositeIndexSql);
            log.info("SQLite twelvedata_daily_ohlcv table and indexes initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize SQLite twelvedata_daily_ohlcv schema", e);
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    @Override
    public void saveAll(List<OhlcvRecord> records) {
        String sql =
                """
                INSERT OR REPLACE INTO twelvedata_daily_ohlcv
                (symbol, date, open, high, low, close, volume)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (OhlcvRecord ohlcv : records) {
                pstmt.setString(1, ohlcv.symbol());
                pstmt.setString(2, ohlcv.date().toString());
                pstmt.setDouble(3, ohlcv.open());
                pstmt.setDouble(4, ohlcv.high());
                pstmt.setDouble(5, ohlcv.low());
                pstmt.setDouble(6, ohlcv.close());
                pstmt.setLong(7, ohlcv.volume());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            log.debug("Saved {} OHLCV records", records.size());
        } catch (SQLException e) {
            log.error("Failed to save OHLCV records", e);
            throw new IllegalStateException("Failed to save OHLCV records", e);
        }
    }

    @Override
    public List<OhlcvRecord> findBySymbol(String symbol, int days) {
        String sql =
                """
                SELECT symbol, date, open, high, low, close, volume
                FROM twelvedata_daily_ohlcv
                WHERE symbol = ? AND date >= date('now', ?)
                ORDER BY date ASC
                """;

        List<OhlcvRecord> results = new ArrayList<>();
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
            log.error("Failed to find OHLCV records for symbol {} over {} days", symbol, days, e);
            throw new IllegalStateException("Failed to find OHLCV records", e);
        }
        return results;
    }

    private OhlcvRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        return new OhlcvRecord(
                rs.getString("symbol"),
                LocalDate.parse(rs.getString("date")),
                rs.getDouble("open"),
                rs.getDouble("high"),
                rs.getDouble("low"),
                rs.getDouble("close"),
                rs.getLong("volume"));
    }

    @Override
    public int deleteBySymbol(String symbol) {
        String sql = "DELETE FROM twelvedata_daily_ohlcv WHERE symbol = ?";
        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            int deleted = pstmt.executeUpdate();
            log.info("Deleted {} OHLCV records for symbol {}", deleted, symbol);
            return deleted;
        } catch (SQLException e) {
            log.error("Failed to delete OHLCV records for symbol {}", symbol, e);
            throw new IllegalStateException("Failed to delete OHLCV records", e);
        }
    }
}
