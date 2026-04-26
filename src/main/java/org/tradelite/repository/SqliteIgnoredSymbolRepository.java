package org.tradelite.repository;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteIgnoredSymbolRepository {

    private final JdbcTemplate jdbcTemplate;

    public record IgnoredSymbolRow(long ignoredAt, Integer alertThreshold) {}

    public void save(String symbol, String reason, long ignoredAt, Integer alertThreshold) {
        String sql =
                """
                INSERT OR REPLACE INTO ignored_symbols
                (symbol, reason, ignored_at, alert_threshold)
                VALUES (?, ?, ?, ?)
                """;

        jdbcTemplate.update(sql, symbol, reason, ignoredAt, alertThreshold);
        log.debug("Saved ignored symbol: {} reason: {}", symbol, reason);
    }

    public Optional<IgnoredSymbolRow> findBySymbolAndReason(String symbol, String reason) {
        String sql =
                """
                SELECT ignored_at, alert_threshold
                FROM ignored_symbols
                WHERE symbol = ? AND reason = ?
                """;

        List<IgnoredSymbolRow> results =
                jdbcTemplate.query(
                        sql,
                        (rs, rowNum) -> {
                            long ignoredAt = rs.getLong("ignored_at");
                            int threshold = rs.getInt("alert_threshold");
                            Integer alertThreshold = rs.wasNull() ? null : threshold;
                            return new IgnoredSymbolRow(ignoredAt, alertThreshold);
                        },
                        symbol,
                        reason);
        return results.stream().findFirst();
    }

    public void deleteExpiredEntries(long cutoffEpochSeconds) {
        String sql = "DELETE FROM ignored_symbols WHERE ignored_at < ?";
        int deleted = jdbcTemplate.update(sql, cutoffEpochSeconds);
        if (deleted > 0) {
            log.info("Deleted {} expired ignored symbol entries", deleted);
        }
    }
}
