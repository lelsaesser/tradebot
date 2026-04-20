package org.tradelite.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
public class SqliteIgnoredSymbolRepository {

    private final DataSource dataSource;

    public record IgnoredSymbolRow(long ignoredAt, Integer alertThreshold) {}

    @Autowired
    public SqliteIgnoredSymbolRepository(DataSource dataSource) {
        this.dataSource = dataSource;
        initializeSchema();
    }

    private void initializeSchema() {
        String createTableSql =
                """
                CREATE TABLE IF NOT EXISTS ignored_symbols (
                    symbol          TEXT    NOT NULL,
                    reason          TEXT    NOT NULL,
                    ignored_at      INTEGER NOT NULL,
                    alert_threshold INTEGER,
                    PRIMARY KEY (symbol, reason)
                )
                """;

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSql);
            log.info("SQLite ignored_symbols table initialized successfully");
        } catch (SQLException e) {
            log.error("Failed to initialize SQLite ignored_symbols schema", e);
            throw new IllegalStateException("Failed to initialize SQLite schema", e);
        }
    }

    public void save(String symbol, String reason, long ignoredAt, Integer alertThreshold) {
        String sql =
                """
                INSERT OR REPLACE INTO ignored_symbols
                (symbol, reason, ignored_at, alert_threshold)
                VALUES (?, ?, ?, ?)
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setString(2, reason);
            pstmt.setLong(3, ignoredAt);
            if (alertThreshold != null) {
                pstmt.setInt(4, alertThreshold);
            } else {
                pstmt.setNull(4, java.sql.Types.INTEGER);
            }
            pstmt.executeUpdate();
            log.debug("Saved ignored symbol: {} reason: {}", symbol, reason);
        } catch (SQLException e) {
            log.error("Failed to save ignored symbol: {} reason: {}", symbol, reason, e);
            throw new IllegalStateException("Failed to save ignored symbol", e);
        }
    }

    public Optional<IgnoredSymbolRow> findBySymbolAndReason(String symbol, String reason) {
        String sql =
                """
                SELECT ignored_at, alert_threshold
                FROM ignored_symbols
                WHERE symbol = ? AND reason = ?
                """;

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, symbol);
            pstmt.setString(2, reason);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long ignoredAt = rs.getLong("ignored_at");
                    int threshold = rs.getInt("alert_threshold");
                    Integer alertThreshold = rs.wasNull() ? null : threshold;
                    return Optional.of(new IgnoredSymbolRow(ignoredAt, alertThreshold));
                }
            }
        } catch (SQLException e) {
            log.error("Failed to find ignored symbol: {} reason: {}", symbol, reason, e);
            throw new IllegalStateException("Failed to find ignored symbol", e);
        }
        return Optional.empty();
    }

    public void deleteExpiredEntries(long cutoffEpochSeconds) {
        String sql = "DELETE FROM ignored_symbols WHERE ignored_at < ?";

        try (Connection conn = dataSource.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, cutoffEpochSeconds);
            int deleted = pstmt.executeUpdate();
            if (deleted > 0) {
                log.info("Deleted {} expired ignored symbol entries", deleted);
            }
        } catch (SQLException e) {
            log.error("Failed to delete expired ignored symbol entries", e);
            throw new IllegalStateException("Failed to delete expired ignored symbol entries", e);
        }
    }
}
